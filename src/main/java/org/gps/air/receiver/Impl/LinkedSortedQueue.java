package org.gps.air.receiver.Impl;

import java.util.Map;
import java.util.TreeMap;

/**
 *
 * Sort of queue implementation that has pointers at both ends and
 * can search elements from start or end and let's insert nodes searching
 * from either ends of the queue.
 *
 * Created by leogps on 6/11/14.
 */
public class LinkedSortedQueue<K extends Comparable<K>, V> {

    private DataHolderNode<K, V> dataHolderNode;

    private DataHolderNode tail;

    private DataHolderNode head;

    public synchronized boolean isEmpty() {
        return head == null /*&& tail == null*/;
    }

    public synchronized K firstKey() {
        if(head == null) {
            return null;
        }
        return (K) head.k;
    }

    public synchronized K firstKeyRemove() {
        if(head == null) {
            return null;
        }

        K key = (K) head.k;

        // Removing first element
        if(head.next == null) {
            clear();
        } else {
            head = head.next;
            head.previous = null;
        }

        return key;
    }

    public synchronized V firstKeyValueRemove() {
        if(head == null) {
            return null;
        }

        V value = (V) head.v;

        // Removing first element
        if(head.next == null) {
            clear();
        } else {
            head = head.next;
            head.previous = null;
        }

        return value;
    }

    public synchronized Entry<K, V> firstEntryRemove() {
        if(head == null) {
            return null;
        }

        Entry<K, V> firstEntry = new Entry<K, V>((K) head.k, (V)head.v);

        // Removing first element
        if(head.next == null) {
            clear();
        } else {
            head = head.next;
            head.previous = null;
        }

        return firstEntry;

    }

    public synchronized V remove(K k) {
        if(head == null) {
            return null;
        }
        return remove(k, false).getValue();
    }

    public synchronized void clear() {
        head = tail = dataHolderNode = null;
    }

    public synchronized void put(K k, V v) {
        put(k, v, true);
    }

    public synchronized void clearSpecified(int count) {

        traverseNodesAndRemove(count);

    }

    private synchronized void traverseNodesAndRemove(int count) {

        if(--count > 0 && head.next != null) {
            head = head.next;
            head.previous = null;
            traverseNodesAndRemove(count);
        }

    }

    private final class DataHolderNode<K extends Comparable<K>, V> {

        private K k;
        private V v;

        private DataHolderNode<K, V> previous;

        private DataHolderNode<K, V> next;

        public DataHolderNode(K k, V v) {
            this.k = k;
            this.v = v;
        }

        private synchronized void insertNode(DataHolderNode<K, V> newNode) {

            K previousK = this.previous == null ? null : this.previous.k;

            int thisCompareVal = this.k.compareTo(newNode.k);

            if(thisCompareVal == 0) {

                // Re-establish links by replacing 'this'. Duplicates not allowed.
                if(this.previous != null) {
                    this.previous.next = newNode;
                    newNode.previous = this.previous;
                }

                if(this.next != null) {
                    newNode.next = this.next;
                    this.next.previous = newNode;
                }
                return;

            }

            if(previousK != null) { // previous value exists.

                int prevCompareVal = newNode.k.compareTo(previousK);

                if(thisCompareVal > 0 && prevCompareVal > 0) {
                    // Re-establish links by inserting between this and previous.
                    this.previous.next = newNode;
                    newNode.previous = this.previous;
                    newNode.next = this;
                    this.previous = newNode;
                    return;

                } else if(thisCompareVal > 0) {

                    this.previous.insertNode(newNode);
                    return;

                } else if(thisCompareVal < 0) {

                    if(this.next == null) {

                        // This is the last element, add at the end.
                        this.next = newNode;
                        newNode.previous = this;
                        tail = newNode; // Make tail point to the new node.
                        return;

                    } else {

                        this.next.insertNode(newNode);
                        return;
                    }

                } else {
                    // WTF? How did this happen?
                    throw new RuntimeException("Internal DataStructure seems to be corrupt.");
                }


            } else { // Previous value does not exist.

                if(thisCompareVal > 0) {
                    // Make newNode the first element.
                    this.previous = newNode;
                    newNode.next = this;
                    head = newNode; // Make head to point to the newNode.

                } else if(thisCompareVal < 0) {
                    if(this.next == null) {

                        // This is the last element, add at the end.
                        this.next = newNode;
                        newNode.previous = this;
                        tail = newNode; // Make tail to point to the new node.
                        return;

                    } else {

                        this.next.insertNode(newNode);
                        return;
                    }
                } else {
                    // WTF? How did this happen?
                    throw new RuntimeException("Internal DataStructure seems to be corrupt.");
                }

            }


        }

        public synchronized void print() {
            System.out.println( "{ key: " + this.k + ", value: " + this.v + " }");
            System.out.println( "\n");
            if(this.next != null) {
                this.next.print();
            }
        }
    }



    public synchronized Entry<K, V> remove(K key, boolean startFromLast) {

        if(head == null) {
            return null;
        }

        // TODO: Conquer and Divide?
        DataHolderNode<K, V> node;
        if(startFromLast) {

             node = find(key, tail, startFromLast);

        } else {

            node = find(key, head, startFromLast);

        }


        if(node == null) {
            return null;
        }

        Entry<K, V> entry = new Entry<K, V>(node.k, node.v);

        if(node == head) {

            // Removing first element
            if(head.next == null) {
                clear();
            } else {
                head = head.next;
                head.previous = null;
            }

        } else if(node == tail) {

            // Removing last element
            if(tail.previous == null) {
                clear();
            } else {
                tail = tail.previous;
                tail.next = null;
            }

        } else {

            // Re-establishing link by removing matched node from the chain.
            node.previous.next = node.next;
            node.next.previous = node.previous;

        }

        return entry;
    }

    private synchronized DataHolderNode<K, V> find(K key, DataHolderNode node, boolean startFromLast) {

        if(node.k.compareTo(key) == 0) {
            return node;
        }
        if(startFromLast) {

            if(node.previous != null) {
                return find(key, node.previous, startFromLast);
            }


        } else if(!startFromLast) {

            if(node.next != null) {
                return find(key, node.next, startFromLast);
            }

        }

        return null;

    }


    public synchronized void put(K k, V v, boolean startFromLast) {

        DataHolderNode<K, V> newNode = new DataHolderNode<K, V>(k, v);

        if(head == null) {

            // First element insertion.
            this.dataHolderNode = newNode;

            this.head = dataHolderNode;
            this.tail = dataHolderNode;

        } else {

            if(startFromLast) {
                this.tail.insertNode(newNode);
            } else {
                this.head.insertNode(newNode);
            }

        }
    }

    public synchronized void print() {
        head.print();
    }

    public synchronized int size() {
        if(head == null) {
            return 0;
        }
        return doCountElements(1, head);
    }

    private synchronized int doCountElements(int count, DataHolderNode node) {
        if(node.next != null) {
            return doCountElements(++count, node.next);
        }

        return count;
    }

    public synchronized Map<K, V> getElementMap() {
        Map<K, V> elementMap = new TreeMap<K, V>();

        addNodesToMap(head, elementMap);

        return elementMap;
    }



    private synchronized void addNodesToMap(DataHolderNode<K, V> node, Map<K, V> map) {
        map.put(node.k, node.v);
        if(node.next != null) {
            addNodesToMap(node.next, map);
        }
    }

}
