/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.clients;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The set of requests which have been sent or are being sent but haven't yet received a responseq
 * 请求队列
 *       1. 正在发送的请求
 *       2. 已经发送的但还没有接收到response的请求;
 */
final class InFlightRequests {
    /**
     * 每个连接最大执行中请求数
     */
    private final int maxInFlightRequestsPerConnection;
    /**
     * node->Deque<ClientRequest>
     *     双端队列Deque，其中的元素请求ClientRequest
     *     新请求从队列头部插入。发送从队列尾部取出
     */
    private final Map<String, Deque<NetworkClient.InFlightRequest>> requests = new HashMap<>();
    /**
     * Thread safe total number of in flight requests.
     */
    private final AtomicInteger inFlightRequestCount = new AtomicInteger(0);

    public InFlightRequests(int maxInFlightRequestsPerConnection) {
        this.maxInFlightRequestsPerConnection = maxInFlightRequestsPerConnection;
    }

    /**
     * Add the given request to the queue for the connection it was directed to
     * 请求入队
     */
    public void add(NetworkClient.InFlightRequest request) {
        // 目的地
        String destination = request.destination;
        // 获取目的地请求队列，没有则新建一个ArrayDeque
        Deque<NetworkClient.InFlightRequest> reqs = this.requests.get(destination);
        if (reqs == null) {
            reqs = new ArrayDeque<>();
            this.requests.put(destination, reqs);
        }
        // 向头部添加请求
        reqs.addFirst(request);
        // 请求消息个数+1，
        inFlightRequestCount.incrementAndGet();
    }

    /**
     * Get the request queue for the given node
     *
     * 获取指定noded的请求队列
     */
    private Deque<NetworkClient.InFlightRequest> requestQueue(String node) {
        Deque<NetworkClient.InFlightRequest> reqs = requests.get(node);
        if (reqs == null || reqs.isEmpty())
            throw new IllegalStateException("There are no in-flight requests for node " + node);
        return reqs;
    }

    /**
     * Get the oldest request (the one that will be completed next) for the given node
     *
     * 请求出队
     */
    public NetworkClient.InFlightRequest completeNext(String node) {
        // 从指定的请求队列的尾部取出一个请求
        NetworkClient.InFlightRequest inFlightRequest = requestQueue(node).pollLast();
        // 请求数量-1
        inFlightRequestCount.decrementAndGet();
        return inFlightRequest;
    }

    /**
     * Get the last request we sent to the given node (but don't remove it from the queue)
     *
     * @param node The node id
     */
    public NetworkClient.InFlightRequest lastSent(String node) {
        return requestQueue(node).peekFirst();
    }

    /**
     * Complete the last request that was sent to a particular node.
     *
     * @param node The node the request was sent to
     * @return The request
     */
    public NetworkClient.InFlightRequest completeLastSent(String node) {
        NetworkClient.InFlightRequest inFlightRequest = requestQueue(node).pollFirst();
        inFlightRequestCount.decrementAndGet();
        return inFlightRequest;
    }

    /**
     * Can we send more requests to this node?
     * 
     * 校验请求队列是否满了
     * 
     * @param node Node in question
     * @return true iff we have no requests still being sent to the given node
     *
     *         重点在于queue.peekFirst().request().completed， 即如果发给这个节点的最早的请求还没有发送完成，是不能再往这个节点发送请求的。
     * 
     *         从canSendMore方法中也可以看出， 只要没有超过maxInFlightRequestsPerConnection，一个node可以有多个in-flight request的
     *
     *         maxInFlightRequestsPerConnection max.in.flight.requests.per.connection
     */
    public boolean canSendMore(String node) {
        Deque<NetworkClient.InFlightRequest> queue = requests.get(node);
        return queue == null || queue.isEmpty() ||
                (queue.peekFirst().send.completed() && queue.size() < this.maxInFlightRequestsPerConnection);
    }

    /**
     * Return the number of in-flight requests directed at the given node
     *
     * 获取请求队列中的请求数量
     * @param node The node
     * @return The request count.
     */
    public int count(String node) {
        Deque<NetworkClient.InFlightRequest> queue = requests.get(node);
        return queue == null ? 0 : queue.size();
    }

    /**
     * Return true if there is no in-flight request directed at the given node and false otherwise
     */
    public boolean isEmpty(String node) {
        Deque<NetworkClient.InFlightRequest> queue = requests.get(node);
        return queue == null || queue.isEmpty();
    }

    /**
     * Count all in-flight requests for all nodes. This method is thread safe, but may lag the actual count.
     */
    public int count() {
        return inFlightRequestCount.get();
    }

    /**
     * Return true if there is no in-flight request and false otherwise
     */
    public boolean isEmpty() {
        for (Deque<NetworkClient.InFlightRequest> deque : this.requests.values()) {
            if (!deque.isEmpty())
                return false;
        }
        return true;
    }

    /**
     * Clear out all the in-flight requests for the given node and return them
     *
     * @param node The node
     * @return All the in-flight requests for that node that have been removed
     */
    public Iterable<NetworkClient.InFlightRequest> clearAll(String node) {
        Deque<NetworkClient.InFlightRequest> reqs = requests.get(node);
        if (reqs == null) {
            return Collections.emptyList();
        } else {
            final Deque<NetworkClient.InFlightRequest> clearedRequests = requests.remove(node);
            inFlightRequestCount.getAndAdd(-clearedRequests.size());
            return new Iterable<NetworkClient.InFlightRequest>() {
                @Override
                public Iterator<NetworkClient.InFlightRequest> iterator() {
                    return clearedRequests.descendingIterator();
                }
            };
        }
    }

    private Boolean hasExpiredRequest(long now, Deque<NetworkClient.InFlightRequest> deque) {
        for (NetworkClient.InFlightRequest request : deque) {
            long timeSinceSend = Math.max(0, now - request.sendTimeMs);
            if (timeSinceSend > request.requestTimeoutMs)
                return true;
        }
        return false;
    }

    /**
     * Returns a list of nodes with pending in-flight request, that need to be timed out
     *
     * @param now current time in milliseconds
     * @return list of nodes
     */
    public List<String> nodesWithTimedOutRequests(long now) {
        List<String> nodeIds = new ArrayList<>();
        for (Map.Entry<String, Deque<NetworkClient.InFlightRequest>> requestEntry : requests.entrySet()) {
            String nodeId = requestEntry.getKey();
            Deque<NetworkClient.InFlightRequest> deque = requestEntry.getValue();
            if (hasExpiredRequest(now, deque))
                nodeIds.add(nodeId);
        }
        return nodeIds;
    }

}
