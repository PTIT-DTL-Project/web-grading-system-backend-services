package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.PendingAction;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.PendingActionRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.RunRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.UserPrincipal;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WriteApprovalService {
    public record ChatPendingAction(UUID id, String toolName, String description, JsonNode arguments) {
        public String summary() {
            return description + " (" + toolName + ") với tham số " + arguments;
        }
    }

    private final PendingActionRepository pendingActions;
    private final RunRepository runs;
    private final ConversationRepository conversations;
    private final List<AgentTool> tools;
    private final ObjectMapper mapper;
    private final vn.edu.ptit.web_grading_system.ai_assistant_service.config.GatewayTrustProperties gateway;
    private final PlatformTransactionManager transactionManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ToolResult propose(AgentTool tool, JsonNode args, AgentContext context) {
        if (context.runId() == null || context.userId() == null) {
            return ToolResult.error("APPROVAL_CONTEXT_MISSING", "Không thể tạo yêu cầu duyệt", false);
        }
        if (tool.getDefinition().effectType() != EffectType.DOMAIN_WRITE
                || context.roles() == null
                || context.roles().stream().noneMatch(tool.getDefinition().requiredRoles()::contains)) {
            return ToolResult.error("FORBIDDEN", "Không có quyền tạo yêu cầu này", false);
        }
        if (tool instanceof vn.edu.ptit.web_grading_system.ai_assistant_service.tools.DomainApiTool domainTool) {
            ToolResult invalid = domainTool.validateArguments(args);
            if (invalid != null) return invalid;
        }
        if (pendingActions.findPendingByConversationId(context.conversationId()).stream()
                .anyMatch(action -> action.getExpiresAt() != null && action.getExpiresAt().isAfter(Instant.now()))) {
            return ToolResult.error("PENDING_ACTION_EXISTS",
                    "Đang có thao tác chờ đồng ý trong chat; hãy trả lời thao tác đó trước", false);
        }
        try {
            String payload = mapper.writeValueAsString(args);
            PendingAction action = pendingActions.save(PendingAction.builder()
                    .runId(context.runId()).type("CONFIRM")
                    .questionJson(payload)
                    .optionsJson(mapper.writeValueAsString(java.util.Map.of("toolName", tool.getName())))
                    .status("PENDING")
                    .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                    .build());
            return ToolResult.ok(java.util.Map.of("status", "AWAITING_CONFIRMATION",
                    "pendingActionId", action.getId(), "toolName", tool.getName(), "arguments", args));
        } catch (Exception e) {
            return ToolResult.error("APPROVAL_SAVE_FAILED", "Không lưu được yêu cầu duyệt", false);
        }
    }

    @Transactional(readOnly = true)
    public Object get(UUID id, UserPrincipal principal) {
        PendingAction action = owned(id, principal);
        return view(action);
    }

    @Transactional(readOnly = true)
    public List<Object> list(UUID conversationId, UserPrincipal principal) {
        conversations.findByIdAndOwnerId(conversationId, principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc trò chuyện"));
        return pendingActions.findPendingByConversationId(conversationId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ChatPendingAction> pendingForChat(AgentContext context) {
        conversations.findByIdAndOwnerId(context.conversationId(), context.userId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc trò chuyện"));
        return pendingActions.findPendingByConversationId(context.conversationId()).stream()
                .filter(action -> !action.getRunId().equals(context.runId()))
                .filter(action -> action.getExpiresAt() != null && action.getExpiresAt().isAfter(Instant.now()))
                .findFirst().map(action -> {
                    try {
                        String toolName = mapper.readTree(action.getOptionsJson()).path("toolName").asText();
                        String description = tools.stream().filter(tool -> tool.getName().equals(toolName))
                                .map(AgentTool::getDescription).findFirst().orElse(toolName);
                        return new ChatPendingAction(action.getId(), toolName, description,
                                mapper.readTree(action.getQuestionJson()));
                    } catch (Exception e) {
                        throw new IllegalStateException("Yêu cầu chờ duyệt không hợp lệ", e);
                    }
                });
    }

    public Object confirmFromChat(UUID id, AgentContext context) {
        ChatPendingAction pending = pendingForChat(context)
                .filter(action -> action.id().equals(id))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thao tác chờ trong cuộc trò chuyện"));
        return confirm(pending.id(), principal(context));
    }

    public Object rejectFromChat(UUID id, AgentContext context) {
        ChatPendingAction pending = pendingForChat(context)
                .filter(action -> action.id().equals(id))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thao tác chờ trong cuộc trò chuyện"));
        return reject(pending.id(), principal(context));
    }

    private UserPrincipal principal(AgentContext context) {
        return new UserPrincipal(context.userId(), context.email(), context.roles().stream()
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList());
    }

    public Object reject(UUID id, UserPrincipal principal) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> {
            PendingAction action = lockedOwned(id, principal);
            if (!"PENDING".equals(action.getStatus())) throw new IllegalStateException("Yêu cầu không còn chờ duyệt");
            action.setStatus("REJECTED");
            return view(action);
        });
    }

    public Object confirm(UUID id, UserPrincipal principal) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Reservation reservation = transaction.execute(status -> {
            PendingAction action = lockedOwned(id, principal);
            if (!"PENDING".equals(action.getStatus()) || action.getExpiresAt().isBefore(Instant.now()))
                throw new IllegalStateException("Yêu cầu không còn hiệu lực");
            try {
                String toolName = mapper.readTree(action.getOptionsJson()).path("toolName").asText();
                AgentTool tool = tools.stream().filter(t -> t.getName().equals(toolName)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Tool không còn tồn tại"));
                Set<String> roles = principal.authorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
                if (tool.getDefinition().effectType() != EffectType.DOMAIN_WRITE
                        || roles.stream().noneMatch(tool.getDefinition().requiredRoles()::contains))
                    throw new SecurityException("Không có quyền thực hiện thao tác");
                action.setStatus("EXECUTING");
                UUID conversationId = runs.findById(action.getRunId()).orElseThrow().getConversationId();
                return new Reservation(tool, mapper.readTree(action.getQuestionJson()), roles, conversationId, action.getRunId());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("Yêu cầu duyệt không hợp lệ", e);
            }
        });
        if (reservation == null) throw new IllegalStateException("Không thể giữ yêu cầu duyệt");
        ToolResult result;
        try {
            AgentContext context = new AgentContext(principal.userId(), principal.email(), reservation.roles(),
                    reservation.conversationId(), reservation.runId(), gateway.secret());
            JsonNode commandArgs = reservation.arguments();
            if (reservation.tool().getName().equals("createAssignment") && commandArgs.isObject()) {
                commandArgs = commandArgs.deepCopy();
                ((com.fasterxml.jackson.databind.node.ObjectNode) commandArgs)
                        .put("idempotencyKey", "ai-" + id);
            }
            result = reservation.tool().execute(commandArgs, context);
        } catch (Exception e) {
            result = ToolResult.error("UNKNOWN_OUTCOME", "Không xác định thao tác đã hoàn thành: " + e.getMessage(), false);
        }
        ToolResult finalResult = result != null ? result : ToolResult.error("EMPTY_RESULT", "Tool không trả kết quả", false);
        String finalStatus = finalResult.isSuccess() ? "COMPLETED"
                : "UNKNOWN_OUTCOME".equals(finalResult.getCode()) ? "UNKNOWN" : "FAILED";
        transaction.executeWithoutResult(status -> {
            PendingAction action = pendingActions.findLockedById(id).orElseThrow();
            action.setStatus(finalStatus);
            try { action.setResultJson(mapper.writeValueAsString(finalResult)); }
            catch (Exception e) { throw new IllegalStateException("Không lưu được kết quả thao tác", e); }
        });
        return java.util.Map.of("pendingActionId", id, "status", finalStatus, "result", finalResult);
    }

    private record Reservation(AgentTool tool, JsonNode arguments, Set<String> roles,
                               UUID conversationId, UUID runId) {}

    private PendingAction owned(UUID id, UserPrincipal principal) {
        PendingAction action = pendingActions.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu"));
        UUID conversationId = runs.findById(action.getRunId()).orElseThrow().getConversationId();
        conversations.findByIdAndOwnerId(conversationId, principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu"));
        return action;
    }

    private PendingAction lockedOwned(UUID id, UserPrincipal principal) {
        PendingAction action = pendingActions.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu"));
        UUID conversationId = runs.findById(action.getRunId()).orElseThrow().getConversationId();
        conversations.findByIdAndOwnerId(conversationId, principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu"));
        return action;
    }

    private Object view(PendingAction action) {
        try {
            java.util.Map<String, Object> view = new java.util.LinkedHashMap<>();
            view.put("pendingActionId", action.getId());
            view.put("status", action.getStatus());
            view.put("toolName", mapper.readTree(action.getOptionsJson()).path("toolName").asText());
            view.put("arguments", mapper.readTree(action.getQuestionJson()));
            view.put("expiresAt", action.getExpiresAt());
            if (action.getResultJson() != null) view.put("result", mapper.readTree(action.getResultJson()));
            return view;
        } catch (Exception e) { throw new IllegalStateException("Yêu cầu duyệt không hợp lệ", e); }
    }
}
