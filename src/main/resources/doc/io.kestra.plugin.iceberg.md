# How to use the Apache Iceberg plugin

The Apache Iceberg plugin enables Kestra to manage Apache Iceberg namespaces and tables via REST catalogs.

The plugin interacts with Apache Iceberg catalogs through standard REST catalog endpoints. For more details on the REST catalog specification and server configuration, refer to the official [Apache Iceberg REST catalog documentation](https://iceberg.apache.org/docs/latest/rest-catalog/).

You can automate data lake control-plane operations directly from your Kestra workflows, including creating and dropping namespaces, inspecting namespaces, and defining tables with schema columns or raw JSON schemas, partition transforms, and table properties.

## Authentication

All Iceberg tasks configure catalog access through the `catalogConfig` property. The current release supports the Apache Iceberg REST catalog (`type: rest`).

To manage credentials securely without hardcoding sensitive values into flow code, use Kestra [secrets](https://kestra.io/docs/concepts/secret). You can also configure default catalog parameters across tasks using [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

### Catalog Configuration (`catalogConfig`)

`catalogConfig` is a key-value map passed directly to the Iceberg catalog client:

- `type` (required): Must be set to `rest`.
- `uri` (required): The endpoint URL of the Apache Iceberg REST catalog (e.g. `https://iceberg-catalog.example.com:8181`).
- `credential` (optional): OAuth2 client credential in the format `<client-id>:<client-secret>` for authenticating with the REST catalog server. Sensitive values are automatically redacted in task execution logs.
- `token` (optional): Bearer token for REST catalog authentication, as an alternative to `credential`. Sensitive values are automatically redacted in task execution logs.
- `warehouse` (optional): Warehouse identifier or storage location configured in the REST catalog.

All properties in `catalogConfig` are also forwarded to the Hadoop Configuration, allowing storage and FileIO properties (such as S3 endpoint configuration or custom `io-impl` settings) to be passed through to the underlying catalog implementation.

Example catalog configuration:

```yaml
catalogConfig:
  type: rest
  uri: "https://iceberg-catalog.example.com:8181"
  credential: "{{ secret('ICEBERG_CREDENTIAL') }}"
  warehouse: "analytics"
```

## Tasks

The plugin provides five catalog tasks in `io.kestra.plugin.iceberg.catalog`:

### CreateNamespace

Creates a new namespace (database) in the Iceberg catalog.

**Supported properties:**
- `namespace` (required): The namespace or database to create (e.g. `analytics` or multi-level `analytics.marketing`).
- `properties` (optional): Key-value metadata properties for the namespace (e.g. `location`, `comment`).
- `ifNotExists` (optional, default: `true`): If `true`, skips creation without failing when the namespace already exists.

Example:

```yaml
id: create_namespace
type: io.kestra.plugin.iceberg.catalog.CreateNamespace
catalogConfig:
  type: rest
  uri: "https://iceberg-catalog.example.com:8181"
  credential: "{{ secret('ICEBERG_CREDENTIAL') }}"
namespace: "analytics.marketing"
ifNotExists: true
```

### DropNamespace

Drops an existing namespace (database) from the Iceberg catalog.

**Supported properties:**
- `namespace` (required): The namespace or database to drop (e.g. `analytics` or multi-level `analytics.scratch`).
- `ifExists` (optional, default: `true`): If `true`, does not fail when the namespace does not exist.
- `cascade` (optional, default: `false`): If `true`, drops all tables within the namespace before dropping the namespace itself.

Example:

```yaml
id: drop_namespace
type: io.kestra.plugin.iceberg.catalog.DropNamespace
catalogConfig:
  type: rest
  uri: "https://iceberg-catalog.example.com:8181"
  credential: "{{ secret('ICEBERG_CREDENTIAL') }}"
namespace: "analytics.scratch"
ifExists: true
cascade: false
```

### ListNamespaces

Lists child namespaces under a parent namespace, or top-level namespaces if none is specified.

**Supported properties:**
- `namespace` (optional): Parent namespace to list child namespaces under. If omitted or empty, lists top-level namespaces.

Example:

```yaml
id: list_namespaces
type: io.kestra.plugin.iceberg.catalog.ListNamespaces
catalogConfig:
  type: rest
  uri: "https://iceberg-catalog.example.com:8181"
  credential: "{{ secret('ICEBERG_CREDENTIAL') }}"
namespace: "analytics"
```

### CreateTable

Creates a new Apache Iceberg table in the catalog with column definitions or raw schema JSON, partition specs, and properties.

**Supported properties:**
- `namespace` (required): The namespace or database of the Iceberg table (e.g. `analytics`).
- `tableName` (required): The name of the Iceberg table.
- `columns` (optional): List of column definitions with `name` (required), `type` (required, e.g. `string`, `long`, `timestamptz`), `required` (optional, default: `false`), and `doc` (optional). Mutually exclusive with `schemaJson`; exactly one must be supplied.
- `schemaJson` (optional): Raw Iceberg schema in JSON format. Mutually exclusive with `columns`; exactly one must be supplied.
- `partitionFields` (optional): List of column names or transform expressions for partitioning (e.g. `days(event_time)`, `identity(region)`, `bucket(16, id)`).
- `tableProperties` (optional): Table configuration properties (e.g. `write.format.default: parquet`).
- `location` (optional): Custom storage location (URI) for table data and metadata.
- `ifNotExists` (optional, default: `true`): If `true`, does not fail if the table already exists.

Examples:

Create an Iceberg table with column definitions and partitioning:

```yaml
id: create_table
type: io.kestra.plugin.iceberg.catalog.CreateTable
catalogConfig:
  type: rest
  uri: "https://iceberg-catalog.example.com:8181"
  credential: "{{ secret('ICEBERG_CREDENTIAL') }}"
namespace: "analytics"
tableName: "events"
columns:
  - name: id
    type: long
    required: true
    doc: "Unique event identifier"
  - name: event_time
    type: timestamptz
    required: true
  - name: region
    type: string
  - name: payload
    type: string
partitionFields:
  - "days(event_time)"
  - "identity(region)"
tableProperties:
  write.format.default: parquet
ifNotExists: true
```

Create an Iceberg table using raw schema JSON:

```yaml
id: create_table_json
type: io.kestra.plugin.iceberg.catalog.CreateTable
catalogConfig:
  type: rest
  uri: "https://iceberg-catalog.example.com:8181"
  credential: "{{ secret('ICEBERG_CREDENTIAL') }}"
namespace: "analytics"
tableName: "raw_events"
schemaJson: |
  {
    "type": "struct",
    "fields": [
      {"id": 1, "name": "id", "required": true, "type": "long"},
      {"id": 2, "name": "payload", "required": false, "type": "string"}
    ]
  }
ifNotExists: true
```

### DropTable

Drops an Apache Iceberg table from the catalog, optionally purging underlying data and metadata files.

**Supported properties:**
- `namespace` (required): The namespace or database of the Iceberg table.
- `tableName` (required): The name of the Iceberg table to drop.
- `purge` (optional, default: `false`): If `true`, completely deletes underlying data and metadata files instead of just removing catalog metadata.
- `ifExists` (optional, default: `true`): If `true`, does not fail when the table does not exist.

Example:

```yaml
id: drop_table
type: io.kestra.plugin.iceberg.catalog.DropTable
catalogConfig:
  type: rest
  uri: "https://iceberg-catalog.example.com:8181"
  credential: "{{ secret('ICEBERG_CREDENTIAL') }}"
namespace: "analytics"
tableName: "events"
purge: false
ifExists: true
```
