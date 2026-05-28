# Composite Structure

## Overview

A **composite structure** represents a logical grouping of namespaces or services that are treated as a single dynamic unit.

The composite structure defines **membership only** — it does not describe behavior, configuration, or lifecycle of individual members. Its purpose is to provide a consistent and up-to-date view of which entities belong to a composite at any given time.

---

## Composite Identifier

Each composite is identified by a unique **composite ID**.

The composite ID:

* is used as a key prefix in Consul,
* is included in all composite structure notifications,
* uniquely identifies a composite across the platform.

---

## Composite Members

A **composite member** is a namespace (or logical unit) that belongs to a composite.

Characteristics:

* Membership is **dynamic** — members can be added at runtime.
* The member list represents the **current desired state** of the composite.
* Ordering of members is not significant; membership is treated as a set.

---

## Storage Model

### Source of Truth

The composite structure is stored in **Consul**, which acts as the single source of truth.

For each composite:

* Members are stored as a set of keys under a composite-specific prefix.
* The presence or absence of a key determines membership.

---

### Modify Index

When reading composite membership from Consul, the response includes a **modify index**.

The modify index:

* is a monotonically increasing value maintained by Consul,
* changes whenever any member is added or removed,
* represents the **version of the composite structure**.

If the composite membership does not change, the modify index remains the same.

---

## Composite Structure Model

Conceptually, a composite structure consists of:

* **Composite ID**
* **Member set**
* **Modify index**

This can be represented as:

```text
CompositeStructure
  ├─ compositeId
  ├─ members (set)
  └─ modifyIndex
```

The modify index applies to the structure as a whole, not to individual members.

---

## Propagation and Consumers

The composite structure is propagated to external systems as a **snapshot**, not as a delta.

Each propagation includes:

* the full member set,
* the current modify index.

Consumers may use the modify index to:

* detect whether the structure has changed since the last update,
* ignore duplicate updates,
* enforce ordering or freshness constraints.

The interpretation of the modify index is the responsibility of the consumer.
