package vn.edu.ptit.web_grading_system.executor_service.service.infra;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortAllocatorTest {

    @Test
    void claim_returnsPortsInRange() {
        PortAllocator allocator = new PortAllocator();
        int port = allocator.claim();
        assertTrue(port >= PortAllocator.MIN_PORT && port <= PortAllocator.MAX_PORT);
    }

    @Test
    void claim_twice_returnsDistinctPorts() {
        PortAllocator allocator = new PortAllocator();
        int first = allocator.claim();
        int second = allocator.claim();
        assertTrue(first != second);
    }

    @Test
    void release_makesPortReusable() {
        PortAllocator allocator = new PortAllocator();
        int port = allocator.claim();
        allocator.release(port);
        allocator.release(port + 1);
        int again = allocator.claim();
        assertEquals(port, again);
    }

    @Test
    void release_outOfRange_isIgnored() {
        PortAllocator allocator = new PortAllocator();
        allocator.release(1);
        allocator.release(99999);
        int port = allocator.claim();
        assertEquals(PortAllocator.MIN_PORT, port);
    }

    @Test
    void claim_skipsPortAlreadyBoundInThePod() throws Exception {
        // Mirrors the incident: a container left behind by a failed compose boot
        // holds 20000 in the pod's network namespace, so Docker rejects the next
        // bind. The allocator must hand out the next free port instead.
        // Review: 2026-10-29, "Bind for 0.0.0.0:20000 failed" incident.
        try (ServerSocket busy = new ServerSocket()) {
            busy.setReuseAddress(false);
            busy.bind(new InetSocketAddress("0.0.0.0", PortAllocator.MIN_PORT));
            PortAllocator allocator = new PortAllocator();

            int claimed = allocator.claim();

            assertTrue(claimed > PortAllocator.MIN_PORT,
                    "allocator handed out a port that is already bound: " + claimed);
            assertTrue(allocator.claim() > claimed);
        }
    }
}
