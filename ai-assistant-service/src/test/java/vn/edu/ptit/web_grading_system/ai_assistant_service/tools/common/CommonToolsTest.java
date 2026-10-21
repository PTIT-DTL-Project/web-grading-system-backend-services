package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.common;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ClassSummaryDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommonToolsTest {

    @Test
    void testListMyClassesToolExecutes() {
        CourseServiceClient client = mock(CourseServiceClient.class);
        ListMyClassesTool tool = new ListMyClassesTool(client);

        ClassSummaryDto cls = new ClassSummaryDto(UUID.randomUUID(), "INT1434", "Kiến trúc máy tính", "Mô tả", "ACTIVE");
        PageDto<ClassSummaryDto> page = new PageDto<>(List.of(cls), 1, 1, 20, 0);

        when(client.listStudentClasses(any(), any(), anyInt(), anyInt(), any())).thenReturn(page);

        AgentContext ctx = new AgentContext(UUID.randomUUID(), "test@ptit.edu.vn", Set.of("ROLE_STUDENT"), UUID.randomUUID(), UUID.randomUUID(), "secret");
        ToolResult result = tool.execute(null, ctx);
        assertTrue(result.isSuccess());
        assertEquals("OK", result.getCode());
    }
}
