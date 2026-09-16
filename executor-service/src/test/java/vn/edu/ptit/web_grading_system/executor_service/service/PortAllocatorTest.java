package vn.edu.ptit.web_grading_system.executor_service.service;

import org.junit.jupiter.api.Test;

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
}
