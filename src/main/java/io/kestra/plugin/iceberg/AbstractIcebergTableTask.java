package io.kestra.plugin.iceberg;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;

/**
 * Shared base task for Apache Iceberg table-level tasks.
 * Manages namespace and table name resolution and table identifiers.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractIcebergTableTask extends AbstractIcebergTask {

    @Schema(
        title = "Table namespace",
        description = "The namespace or database of the Iceberg table (e.g. 'analytics' or multi-level 'analytics.raw')"
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> namespace;

    @Schema(
        title = "Table name",
        description = "The name of the Iceberg table"
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> tableName;

    /**
     * Resolves the TableIdentifier by rendering namespace and tableName expressions.
     * Supports dot-separated hierarchical namespaces (e.g. 'analytics.raw' -> Namespace.of("analytics", "raw")).
     *
     * @param runContext The current Kestra RunContext
     * @return The Iceberg TableIdentifier
     */
    protected TableIdentifier tableIdentifier(RunContext runContext) throws Exception {
        var rNamespace = runContext.render(namespace).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("'namespace' must not be null or empty.")
        );
        var rTableName = runContext.render(tableName).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("'tableName' must not be null or empty.")
        );

        var ns = parseNamespace(rNamespace);
        if (ns.isEmpty()) {
            throw new IllegalArgumentException("Invalid namespace: '" + rNamespace + "'. Must contain at least one non-empty level.");
        }

        return TableIdentifier.of(ns, rTableName.trim());
    }
}
