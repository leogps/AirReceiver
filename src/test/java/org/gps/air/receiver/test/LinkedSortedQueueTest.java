package org.gps.air.receiver.test;

import org.gps.air.receiver.Impl.Entry;
import org.gps.air.receiver.Impl.LinkedSortedQueue;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Created by leogps on 6/11/14.
 */
public class LinkedSortedQueueTest {

    private LinkedSortedQueue audioQueue = null;
    private final byte zero = 0;
    private final byte one = 1;
    private final byte[] valByte = {zero, one, zero};

    @Test
    public void testQueue() {

        audioQueue = new LinkedSortedQueue<Long, byte[]>();

        audioQueue.put(9L, valByte, true);
        audioQueue.put(9L, valByte, true); // Duplicate
        audioQueue.put(6L, valByte, true);
        audioQueue.put(1L, valByte, true);
        audioQueue.put(5L, valByte, true);
        audioQueue.put(8L, valByte, true);
        audioQueue.put(2L, valByte, true);
        audioQueue.put(8L, valByte, true); // Duplicate
        audioQueue.put(3L, valByte, true);
        audioQueue.put(1L, valByte, true); // Duplicate
        audioQueue.put(4L, valByte, true);
        audioQueue.put(9L, valByte, true); // Duplicate
        audioQueue.put(7L, valByte, true);

        doTestQueue();

    }

    private void doTestQueue() {
        Map<Long, byte[]> elementMap = audioQueue.getElementMap();

        long previousValue = 0;
        for(Long key : elementMap.keySet()) {
            System.out.println("{ key: " + key + ", value: " + elementMap.get(key) + " }");
            Assert.assertTrue(key > previousValue);
            previousValue = key;
        }
    }

    @Test
    public void testQueueWithAscendingValues() {

        audioQueue = new LinkedSortedQueue<Long, byte[]>();

        audioQueue.put(1L, valByte, true);
        audioQueue.put(2L, valByte, true);
        audioQueue.put(3L, valByte, true);
        audioQueue.put(4L, valByte, true);
        audioQueue.put(5L, valByte, true);
        audioQueue.put(6L, valByte, true);
        audioQueue.put(7L, valByte, true);
        audioQueue.put(8L, valByte, true);
        audioQueue.put(9L, valByte, true);
        audioQueue.put(10L, valByte, true);
        audioQueue.put(11L, valByte, true);
        audioQueue.put(12L, valByte, true);
        audioQueue.put(13L, valByte, true);

        doTestQueue();
    }

    @Test
    public void testQueueWithDescendingValues() {

        audioQueue = new LinkedSortedQueue<Long, byte[]>();

        audioQueue.put(13L, valByte, true);
        audioQueue.put(12L, valByte, true); // Duplicate
        audioQueue.put(11L, valByte, true);
        audioQueue.put(10L, valByte, true);
        audioQueue.put(9L, valByte, true);
        audioQueue.put(8L, valByte, true);
        audioQueue.put(7L, valByte, true);
        audioQueue.put(6L, valByte, true); // Duplicate
        audioQueue.put(5L, valByte, true);
        audioQueue.put(4L, valByte, true); // Duplicate
        audioQueue.put(3L, valByte, true);
        audioQueue.put(2L, valByte, true); // Duplicate
        audioQueue.put(1L, valByte, true);

        doTestQueue();
    }

    @Test
    public void testQueueRemove() {

        audioQueue = new LinkedSortedQueue<Long, byte[]>();

        audioQueue.put(1L, valByte, true);
        audioQueue.put(2L, valByte, true);
        audioQueue.put(3L, valByte, true);
        audioQueue.put(4L, valByte, true);
        audioQueue.put(5L, valByte, true);
        audioQueue.put(6L, valByte, true);
        audioQueue.put(7L, valByte, true);
        audioQueue.put(8L, valByte, true);
        audioQueue.put(9L, valByte, true);
        audioQueue.put(10L, valByte, true);
        audioQueue.put(11L, valByte, true);
        audioQueue.put(12L, valByte, true);
        audioQueue.put(13L, valByte, true);
        // Total 13 elements

        int initialSize = audioQueue.size();
        Assert.assertEquals(initialSize, 13);

        System.out.println("Testing random element remove in the middle");
        Entry<Long, byte[]> entry1 = audioQueue.remove(6L, true);
        Assert.assertEquals(entry1.getKey().longValue(), 6L);
        Assert.assertEquals(audioQueue.size(), initialSize - 1);

        doTestQueue();

        Entry<Long, byte[]> entry2 = audioQueue.remove(10L, true);
        Assert.assertEquals(entry2.getKey().longValue(), 10L);
        Assert.assertEquals(audioQueue.size(), initialSize - 2);

        doTestQueue();

        System.out.println("Testing unavailable element remove");
        Entry<Long, byte[]> entry3 = audioQueue.remove(10L, true); // Testing unavailable element remove
        Assert.assertNull(entry3);
        Assert.assertEquals(audioQueue.size(), initialSize - 2);

        doTestQueue();

        System.out.println("Testing first element remove");
        Entry<Long, byte[]> entry4 = audioQueue.remove(1L, true); // Testing first element remove
        Assert.assertEquals(entry4.getKey().longValue(), 1L);
        Assert.assertEquals(audioQueue.size(), initialSize - 3);

        doTestQueue();

        System.out.println("Testing last element remove");
        Entry<Long, byte[]> entry5 = audioQueue.remove(13L, true); // Testing last element remove
        Assert.assertEquals(entry5.getKey().longValue(), 13L);
        Assert.assertEquals(audioQueue.size(), initialSize - 4);

        doTestQueue();

    }

    @Test
    public void testEmptyQueueRemove() {
        audioQueue = new LinkedSortedQueue<Long, byte[]>();

        Assert.assertNull(audioQueue.remove(1L));
    }

    @Test
    public void testQueueRemoveAll() {
        audioQueue = new LinkedSortedQueue<Long, byte[]>();
        audioQueue.put(1L, valByte);
        audioQueue.put(100L, valByte);

        Assert.assertNotNull(audioQueue.remove(1L));
        Assert.assertEquals(audioQueue.size(), 1);

        Assert.assertNotNull(audioQueue.remove(100L));
        Assert.assertEquals(audioQueue.size(), 0);
        Assert.assertTrue(audioQueue.isEmpty());


    }

}
