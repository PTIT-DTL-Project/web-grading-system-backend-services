package vn.edu.ptit.web_grading_system.executor_service.service;

import vn.edu.ptit.web_grading_system.executor_service.Constant;

import org.springframework.stereotype.Component;

import java.util.BitSet;

/**
 * Hands out host ports for grading containers. K8s netns isolates pods, so
 * only intra-pod collisions matter — a simple in-memory range is enough.
 */
@Component
public class PortAllocator
{
    static final int MIN_PORT = 20000;
    static final int MAX_PORT = 30000;

    private final BitSet used = new BitSet(MAX_PORT - MIN_PORT + 1);

    public synchronized int claim()
    {
        int idx = used.nextClearBit(0);
        if (idx > MAX_PORT - MIN_PORT)
        {
            throw new IllegalStateException(Constant.Message.NO_FREE_PORTS_PREFIX + MIN_PORT + "-" + MAX_PORT);
        }
        used.set(idx);
        return MIN_PORT + idx;
    }

    public synchronized void release(int port)
    {
        if (port >= MIN_PORT && port <= MAX_PORT)
        {
            used.clear(port - MIN_PORT);
        }
    }
}
