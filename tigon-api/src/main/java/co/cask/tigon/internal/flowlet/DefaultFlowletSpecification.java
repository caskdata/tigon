/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.tigon.internal.flowlet;

import co.cask.tigon.api.ResourceSpecification;
import co.cask.tigon.api.flow.flowlet.FailurePolicy;
import co.cask.tigon.api.flow.flowlet.FlowletSpecification;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * DefaultFlowletSpecification.
 */
public final class DefaultFlowletSpecification implements FlowletSpecification {

  private final String className;
  private final String name;
  private final String description;
  private final FailurePolicy failurePolicy;
  private final Map<String, String> properties;
  private final ResourceSpecification resources;
  private final int maxInstances;

  public DefaultFlowletSpecification(String name, String description,
                                     FailurePolicy failurePolicy, Map<String, String> properties,
                                     ResourceSpecification resources, int maxInstances) {
    this(null, name, description, failurePolicy, properties, resources, maxInstances);
  }

  public DefaultFlowletSpecification(String className, String name,
                                     String description, FailurePolicy failurePolicy,
                                     Map<String, String> properties, ResourceSpecification resources,
                                     int maxInstances) {
    this.className = className;
    this.name = name;
    this.description = description;
    this.failurePolicy = failurePolicy;
    this.properties = properties == null ? ImmutableMap.<String, String>of() : ImmutableMap.copyOf(properties);
    this.resources = resources;
    this.maxInstances = maxInstances;
  }

  @Override
  public String getClassName() {
    return className;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public FailurePolicy getFailurePolicy() {
    return failurePolicy;
  }

  @Override
  public Map<String, String> getProperties() {
    return properties;
  }

  @Override
  public String getProperty(String key) {
    return properties.get(key);
  }

  @Override
  public ResourceSpecification getResources() {
    return resources;
  }

  @Override
  public int getMaxInstances() {
    return maxInstances;
  }
}
