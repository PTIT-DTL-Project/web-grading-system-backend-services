package vn.edu.ptit.web_grading_system.executor_service.service.infra;

import vn.edu.ptit.web_grading_system.executor_service.Constant;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.BitSet;
import java.util.Random;

/**
 * Hands out host ports for grading containers. K8s netns isolates pods, so
 * only intra-pod collisions matter — the in-memory range is the bookkeeping,
 * and a bind probe additionally catches ports already held outside this JVM
 * (a container left behind by a failed compose boot).
 *
 * The starting port is randomized so the allocator never always hands out
 * MIN_PORT (20000) first — this avoids a hot port on every pod start and
 * reduces the chance of colliding with a leftover container from a failed
 * boot. Review: 2026-11-06, "Target port 20000 is not exposed" incident.
 */
@Component
public class PortAllocator
{
    static final int MIN_PORT = 20000;
    static final int MAX_PORT = 30000;

    private final BitSet used = new BitSet(MAX_PORT - MIN_PORT + 1);
    private final Random random = new Random();

    public synchronized int claim()
    {
        int range = MAX_PORT - MIN_PORT + 1;
        int startIdx = random.nextInt(range);
        int idx = used.nextClearBit(startIdx);
        if (idx > MAX_PORT - MIN_PORT)
        {
            idx = used.nextClearBit(0);
        }
        while (idx <= MAX_PORT - MIN_PORT)
        {
            int port = MIN_PORT + idx;
            used.set(idx);
            if (isBindable(port))
            {
                return port;
            }
            // Something outside this JVM already holds the port — typically a
            // container left behind by a failed compose boot, whose host port is
            // published in this pod's network namespace. Stay marked for the rest
            // of the JVM's life so we never hand it out again; the alternative is
            // a container boot that dies on "port is already allocated".
            // Review: 2026-10-29, "Bind for 0.0.0.0:20000 failed" incident.
            idx = used.nextClearBit(idx + 1);
        }
        throw new IllegalStateException(Constant.Message.NO_FREE_PORTS_PREFIX + MIN_PORT + "-" + MAX_PORT);
    }

    /**
     * True when this pod can still bind the port. Grading containers are
     * published inside the pod's shared network namespace, so a leftover
     * container makes the bind fail exactly like it will fail in Docker.
     */
    private static boolean isBindable(int port)
    {
        try (ServerSocket probe = new ServerSocket())
        {
            probe.setReuseAddress(false);
            probe.bind(new InetSocketAddress("0.0.0.0", port));
            return true;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    /**
     * Hands out a host port for the student app's DB service (DB step types).
     * Deliberately reuses the same BitSet as {@link #claim()} — both ports live
     * in the same pod, so sharing one range guarantees no app/DB collision.
     */
    public synchronized int claimDbPort()
    {
        return claim();
    }

    public synchronized void release(int port)
    {
        if (port >= MIN_PORT && port <= MAX_PORT)
        {
            used.clear(port - MIN_PORT);
        }
    }
}
