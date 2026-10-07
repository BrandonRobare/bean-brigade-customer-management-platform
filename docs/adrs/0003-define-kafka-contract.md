# ADR 0003: Define the Kafka interaction event contract

**In short:** In the context of publishing customer interaction events through Kafka, facing the need for 
the producer and consumer to agree on the event format, we decided to version the topic and event contract 
together using `crm.customer.interactions.v1` and `CustomerInteractionRecordedV1`, with customer ID as the
Kafka key and a unique `eventId` for each event.

## Context and Problem Statement

CAP-12 requires a successfully recorded customer interaction to persist in PostgreSQL and emit a versioned
Kafka event. The Kafka producer and consumer need one stable contract for the topic, message key, event 
identity, and versioning rules before they are implemented. What contract should we freeze for customer 
interaction events?
## Decision Drivers

- CAP-12 requires a versioned Kafka event after an interaction is successfully recorded.
- Events for the same customer should stay ordered.
- Kafka delivery is at least once, so consumers must be able to recognize duplicate deliveries.
- The producer and consumer need a stable contract before their PRs are implemented.
- Contract changes should avoid breaking existing consumers when possible.

## Considered Options

1. **Version the topic and event contract together:** use `crm.customer.interactions.v1` with `CustomerInteractionRecordedV1`, key records by customer ID, and give each event a unique `eventId`.
2. **Keep one stable topic and version only the event payload:** use a topic such as `crm.customer.interactions` for every version and rely on the event version field to tell consumers which contract they received.
   
## Decision Outcome

Chosen option: **Version the topic and event contract together**, because it makes the active 
contract clear for both producers and consumers

The topic will be `crm.customer.interactions.v1`, and the event will be `CustomerInteractionRecordedV1`.

The Kafka record will be keyed by `customerId`. This keeps events for the same customer together so 
their ordering can be preserved within a Kafka partition.

Each event will also receive its own unique `eventId`. The consumer can record processed event IDs 
and skip an event if Kafka delivers it again.

The V1 event contains:

- `eventId`
- `eventType`
- `eventVersion`
- `interactionId`
- `customerId`
- `interactionType`
- `correlationId`
- `actor`
- `occurredAt`

The event starts at version 1. Additive changes that do not break existing consumers can remain part of V1. A breaking change to the contract requires a new V2 event contract instead of changing V1 in place.

### Consequences

- Good, because keying by customerId keeps one customer's events ordered within the same Kafka partition.
- Good, because a unique eventId gives the consumer a stable value for duplicate detection.
- Good, because the producer and consumer share one documented event shape.
- Good, because additive changes can be made without unnecessarily creating a new event version.
- Bad, because producers and consumers must continue to support the frozen V1 contract.
- Bad, because a breaking contract change requires a new version rather than changing V1 in place.


### Confirmation

- Publisher tests confirm that `CustomerInteractionRecordedV1` is sent to `crm.customer.interactions.v1`.
- Tests confirm that the Kafka record is keyed by the event's customer ID.
- Published events contain a unique `eventId`.
- Consumer tests confirm that an already processed `eventId` is skipped instead of producing the same side effect twice.
- Tests confirm that the expected V1 fields are present.
- Maven verification passes in CI before the Kafka producer or consumer changes are merged.
- Evidence for the Kafka contract is referenced in the defense evidence index.


## More Information

- Revisit if: the project introduces a schema registry or needs to support several event contract versions at the same time.
- Confidence: high
- Links: CAP-12, Kafka publisher issue, Kafka consumer issue, messaging section of `docs/architecture.md`
