/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.rel.metadata;

import org.apache.calcite.rel.RelNode;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Supplier;

/**
 * Base class for the RelMetadataQuery that uses the metadata handler class
 * generated by the Janino.
 *
 * <p>To add a new implementation to this interface, follow
 * these steps:
 *
 * <ol>
 * <li>Extends {@link RelMetadataQuery} (name it MyRelMetadataQuery for example)
 * to reuse the Calcite builtin metadata query interfaces. In this class, define all the
 * extended Handlers for your metadata and implement the metadata query interfaces.
 * <li>Write your customized provider class <code>RelMdXyz</code>. Follow
 * the pattern from an existing class such as {@link RelMdColumnOrigins},
 * overloading on all of the logical relational expressions to which the query
 * applies.
 * <li>Add a {@code SOURCE} static member to each of your provider class, similar to
 * {@link RelMdColumnOrigins#SOURCE}.
 * <li>Extends {@link DefaultRelMetadataProvider} (name it MyRelMetadataProvider for example)
 * and supplement the "SOURCE"s into the builtin list
 * (This is not required, use {@link ChainedRelMetadataProvider} to chain your customized
 * "SOURCE"s with default ones also works).
 * <li>Set {@code MyRelMetadataProvider} into the cluster instance.
 * <li>Use
 * {@link org.apache.calcite.plan.RelOptCluster#setMetadataQuerySupplier(Supplier)}
 * to set the metadata query {@link Supplier} into the cluster instance. This {@link Supplier}
 * should return a <strong>fresh new</strong> instance.
 * <li>Use the cluster instance to create
 * {@link org.apache.calcite.sql2rel.SqlToRelConverter}.</li>
 * <li>Query your metadata within {@link org.apache.calcite.plan.RelOptRuleCall} with the
 * interfaces you defined in {@code MyRelMetadataQuery}.
 * </ol>
 */
public class RelMetadataQueryBase {
  //~ Instance fields --------------------------------------------------------

  /** Set of active metadata queries, and cache of previous results. */
  public final Table<RelNode, List, Object> map = HashBasedTable.create();

  public final JaninoRelMetadataProvider metadataProvider;

  //~ Static fields/initializers ---------------------------------------------

  public static final ThreadLocal<JaninoRelMetadataProvider> THREAD_PROVIDERS =
      new ThreadLocal<>();

  //~ Constructors -----------------------------------------------------------

  protected RelMetadataQueryBase(JaninoRelMetadataProvider metadataProvider) {
    this.metadataProvider = metadataProvider;
  }

  protected static <H> H initialHandler(Class<H> handlerClass) {
    return handlerClass.cast(
        Proxy.newProxyInstance(RelMetadataQuery.class.getClassLoader(),
            new Class[] {handlerClass}, (proxy, method, args) -> {
              final RelNode r = (RelNode) args[0];
              throw new JaninoRelMetadataProvider.NoHandler(r.getClass());
            }));
  }

  //~ Methods ----------------------------------------------------------------

  /** Re-generates the handler for a given kind of metadata, adding support for
   * {@code class_} if it is not already present. */
  protected <M extends Metadata, H extends MetadataHandler<M>> H
      revise(Class<? extends RelNode> class_, MetadataDef<M> def) {
    return metadataProvider.revise(class_, def);
  }

  /**
   * Removes cached metadata values for specified RelNode.
   *
   * @param rel RelNode whose cached metadata should be removed
   */
  public void clearCache(RelNode rel) {
    map.row(rel).clear();
  }
}
