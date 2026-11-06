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
        assertTrue(port >= PortAllocator.MIN_PORT && port <= PortAllocator.MAX_PORT);
        allocator.release(port);
        // After release, claiming again returns a valid in-range port. The
        // released port remains in the allocator's available pool (its BitSet
        // bit is clear), so subsequent claims stay within range.
        int again = allocator.claim();
        assertTrue(again >= PortAllocator.MIN_PORT && again <= PortAllocator.MAX_PORT);
    }

    @Test
    void release_outOfRange_isIgnored() {
        PortAllocator allocator = new PortAllocator();
        allocator.release(1);
        allocator.release(99999);
        int port = allocator.claim();
        assertTrue(port >= PortAllocator.MIN_PORT && port <= PortAllocator.MAX_PORT,
            "claimed port should be in range even when out-of-range releases are ignored");
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
            int next = allocator.claim();
            assertTrue(next >= PortAllocator.MIN_PORT && next <= PortAllocator.MAX_PORT,
                    "second claim should also be in range");
            assertTrue(next != claimed, "second claim should differ from first");
        }
    }
}
