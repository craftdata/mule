/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.api.dsl.model;

import static com.google.common.collect.ImmutableList.copyOf;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mule.runtime.api.util.Preconditions.checkState;
import static org.mule.runtime.ast.api.ComponentAst.BODY_RAW_PARAM_NAME;

import org.mule.metadata.api.model.MetadataType;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.meta.NamedObject;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.parameter.ParameterizedModel;
import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.dsl.api.component.config.ComponentConfiguration;
import org.mule.runtime.dsl.internal.component.config.InternalComponentConfiguration;
import org.mule.runtime.extension.api.dsl.syntax.DslElementSyntax;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Provides a declaration of how a given {@code model} of type {@code T} is related to its {@link DslElementSyntax DSL}
 * representation.
 * <p>
 * This {@link DslElementModel} can be related to an {@link ComponentConfiguration} of a configuration file by using the
 * {@link #findElement} lookup with the required {@link ComponentIdentifier}, and thus providing a way to relate an
 * {@link ComponentConfiguration} to the {@link ExtensionModel} component or {@link MetadataType} it represents.
 *
 * @since 4.0
 */
public class DslElementModel<T> {

  public static final String COMPONENT_AST_KEY = "ComponentAst";

  private final T model;
  private final String value;
  private final DslElementSyntax dsl;
  private final boolean explicitInDsl;
  private final List<DslElementModel> containedElements;
  private final ComponentConfiguration configuration;
  private final ComponentIdentifier identifier;


  private DslElementModel(T model, DslElementSyntax dsl, List<DslElementModel> containedElements,
                          ComponentConfiguration configuration, String value, boolean explicitInDsl) {
    this.dsl = dsl;
    this.model = model;
    this.containedElements = copyOf(containedElements);
    this.configuration = configuration;
    this.value = value;
    this.identifier = createIdentifier();
    this.explicitInDsl = explicitInDsl;
  }

  /**
   * @return the model associated to {@code this} {@link DslElementModel element}
   */
  public T getModel() {
    return model;
  }

  /**
   * @return the {@link DslElementSyntax} associated to {@code this} {@link DslElementModel element}
   */
  public DslElementSyntax getDsl() {
    return dsl;
  }

  /**
   * @return a {@link List} with all the child {@link DslElementModel elements}
   */
  public List<DslElementModel> getContainedElements() {
    return containedElements;
  }

  /**
   * @return the {@link ComponentIdentifier identifier} associated to {@code this} {@link DslElementModel element}, if one was
   *         provided.
   */
  public Optional<ComponentIdentifier> getIdentifier() {
    return ofNullable(identifier);
  }

  /**
   * @return the {@link ComponentConfiguration} associated to {@code this} {@link DslElementModel element}, if one was provided.
   *
   * @deprecated Use {@link #getComponentModel()} instead.
   */
  @Deprecated
  public Optional<ComponentConfiguration> getConfiguration() {
    return ofNullable(configuration);
  }

  public Optional<ComponentAst> getComponentModel() {
    return configuration == null ? empty() : configuration.getProperty(COMPONENT_AST_KEY).map(cm -> (ComponentAst) cm);
  }

  /**
   * @return the {@code value} assigned to this element in its current configuration. This represents either the value of an
   *         attribute or that of a text child element.
   */
  public Optional<String> getValue() {
    return ofNullable(value);
  }

  /**
   * @return {@code true} if the element represented by {@code this} {@link DslElementModel} has to be explicitly declared in the
   *         DSL, or if it's only present in the internal application representation.
   */
  public boolean isExplicitInDsl() {
    return explicitInDsl;
  }

  /**
   * Lookup method for finding a given {@link DslElementModel} based on its {@link ComponentIdentifier identifier} from
   * {@code this} element as root. If {@code this} {@link DslElementModel} doesn't match with the given identifier, then a DFS
   * lookup is performed for each of its {@link #getContainedElements inner elements}.
   *
   * @param identifier the {@link ComponentIdentifier} used for matching
   * @return the {@link DslElementModel} associated to the given {@code identifier}, if one was found.
   */
  public <E> Optional<DslElementModel<E>> findElement(ComponentIdentifier identifier) {
    if (this.identifier != null && this.identifier.equals(identifier)) {
      return Optional.of((DslElementModel<E>) this);
    }

    return find(e -> e.findElement(identifier));
  }

  /**
   * Lookup method for finding a given {@link DslElementModel} based on its {@code parameterName} from {@code this} element as
   * root. If {@code this} {@link DslElementModel} name doesn't match with the given parameterName, then a DFS lookup is performed
   * for each of its {@link #getContainedElements inner elements}. Since not all the elements in an application may have an
   * {@link DslElementSyntax::getElementName} this lookup method may produce different results than the lookup by
   * {@link ComponentIdentifier identifier}
   *
   * @param modelName the {@code modelName} used for matching
   * @return the {@link DslElementModel} associated to the given {@code identifier}, if one was found.
   */
  public <E> Optional<DslElementModel<E>> findElement(String modelName) {
    if (dsl.getAttributeName().equals(modelName) ||
        (model instanceof NamedObject && ((NamedObject) model).getName().equals(modelName))) {
      return of((DslElementModel<E>) this);
    }

    return find(e -> e.findElement(modelName));
  }

  private ComponentIdentifier createIdentifier() {
    if (configuration != null) {
      return configuration.getIdentifier();
    }

    if (!dsl.supportsTopLevelDeclaration() && !dsl.supportsChildDeclaration()) {
      return null;
    }

    return ComponentIdentifier.builder()
        .name(dsl.getElementName())
        .namespace(dsl.getPrefix())
        .build();
  }

  private <E> Optional<DslElementModel<E>> find(Function<DslElementModel, Optional<DslElementModel>> finder) {
    return containedElements.stream()
        .map(finder)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(e -> (DslElementModel<E>) e)
        .findFirst();
  }

  public static final class Builder<M> {

    private M model;
    private String value;
    private DslElementSyntax dsl;
    private ComponentConfiguration configuration;
    private final List<DslElementModel> contained = new ArrayList<>();
    private boolean explicitInDsl = true;

    private Builder() {}

    public Builder<M> withModel(M model) {
      this.model = model;
      return this;
    }

    public Builder<M> withDsl(DslElementSyntax dsl) {
      this.dsl = dsl;
      return this;
    }

    public Builder<M> containing(DslElementModel element) {
      this.contained.add(element);
      return this;
    }

    /**
     * @deprecated Use {@link #withConfig(ComponentAst)} instead.
     */
    @Deprecated
    public Builder<M> withConfig(ComponentConfiguration element) {
      this.configuration = element;
      return this;
    }

    public Builder<M> withConfig(ComponentAst element) {
      this.configuration = element != null ? from(element) : null;
      return this;
    }

    private ComponentConfiguration from(ComponentAst element) {
      InternalComponentConfiguration.Builder builder = InternalComponentConfiguration.builder()
          .withIdentifier(element.getIdentifier())
          .withValue(element.getRawParameterValue(BODY_RAW_PARAM_NAME).orElse(null));

      element.getModel(ParameterizedModel.class)
          .ifPresent(pm -> element.getParameters()
              .stream()
              .filter(param -> param.getRawValue() != null)
              .forEach(param -> builder.withParameter(param.getModel().getName(), param.getRawValue())));

      element.directChildrenStream().forEach(i -> builder.withNestedComponent(from(i)));
      element.getMetadata().getParserAttributes().forEach(builder::withProperty);
      builder.withComponentLocation(element.getLocation());
      builder.withProperty(COMPONENT_AST_KEY, element);

      return builder.build();
    }

    public Builder<M> withValue(String value) {
      this.value = value;
      return this;
    }

    public Builder<M> isExplicitInDsl(boolean explicit) {
      this.explicitInDsl = explicit;
      return this;
    }

    public DslElementModel<M> build() {
      if (configuration != null) {
        Optional<String> configurationValue = configuration.getValue();
        if (configurationValue.isPresent() && !isBlank(configurationValue.get())) {
          if (value == null) {
            value = configurationValue.get();
          } else {
            checkState(value.equals(configurationValue.get()),
                       "The same element cannot have two different values associated.");
          }
        } else {
          checkState(value == null,
                     "The same element cannot have two different values associated.");
        }
      }

      return new DslElementModel<>(model, dsl, contained, configuration, value, explicitInDsl);
    }

  }

  /**
   * @return a new {@link Builder}
   */
  public static <M> Builder<M> builder() {
    return new Builder<>();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DslElementModel<?> that = (DslElementModel<?>) o;
    return explicitInDsl == that.explicitInDsl &&
        Objects.equals(model, that.model) &&
        Objects.equals(value, that.value) &&
        Objects.equals(dsl, that.dsl) &&
        Objects.equals(containedElements, that.containedElements) &&
        Objects.equals(configuration, that.configuration) &&
        Objects.equals(identifier, that.identifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(model, value, dsl, explicitInDsl, containedElements, configuration, identifier);
  }

  @Override
  public String toString() {
    return DslElementModel.class.getSimpleName() + "(" + (identifier != null ? identifier.toString() : "") + ")";
  }
}
