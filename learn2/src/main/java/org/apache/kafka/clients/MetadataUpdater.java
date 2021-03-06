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

import org.apache.kafka.common.Node;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.requests.MetadataResponse;
import org.apache.kafka.common.requests.RequestHeader;

import java.io.Closeable;
import java.util.List;

/**
 * The interface used by `NetworkClient` to request cluster metadata info to be updated and to retrieve the cluster nodes
 * from such metadata. This is an internal class.
 * 更新metadata 的接口
 * 在NetworkClient 中使用
 * <p>
 * This class is not thread-safe!
 */
public interface MetadataUpdater extends Closeable {

    /**
     * Gets the current cluster info without blocking.
     * 获取cluster信息
     */
    List<Node> fetchNodes();

    /**
     * Returns true if an update to the cluster metadata info is due.
     *
     * metadata 达到了更新时间，并且没有更新的操作正在进行返回true.
     */
    boolean isUpdateDue(long now);

    /**
     * metadata  是否需要更新，此方法返回的是元数据更新的倒计时
     *
     * 返回值是0 就是需要更新，其他大于0的数，就是还需要多久更新
     *
     *
     * Starts a cluster metadata update if needed and possible. Returns the time until the metadata update (which would
     * be 0 if an update has been started as a result of this call).
     * <p>
     * If the implementation relies on `NetworkClient` to send requests, `handleCompletedMetadataResponse` will be
     * invoked after the metadata response is received.
     * <p>
     * The semantics of `needed` and `possible` are implementation-dependent and may take into account a number of
     * factors like node availability, how long since the last metadata update, etc.
     */
    long maybeUpdate(long now);

    /**
     * Handle disconnections for metadata requests.
     * <p>
     * This provides a mechanism for the `MetadataUpdater` implementation to use the NetworkClient instance for its own
     * requests with special handling for disconnections of such requests.
     *
     * @param destination
     */
    void handleDisconnection(String destination);

    /**
     * Handle authentication failure. Propagate the authentication exception if awaiting metadata.
     *
     * @param exception authentication exception from broker
     */
    void handleAuthenticationFailure(AuthenticationException exception);

    /**
     * Handle responses for metadata requests.
     * <p>
     * This provides a mechanism for the `MetadataUpdater` implementation to use the NetworkClient instance for its own
     * requests with special handling for completed receives of such requests.
     */
    void handleCompletedMetadataResponse(RequestHeader requestHeader, long now, MetadataResponse metadataResponse);

    /**
     * Schedules an update of the current cluster metadata info. A subsequent call to `maybeUpdate` would trigger the
     * start of the update if possible (see `maybeUpdate` for more information).
     */
    void requestUpdate();

    /**
     * Close this updater.
     */
    @Override
    void close();
}
