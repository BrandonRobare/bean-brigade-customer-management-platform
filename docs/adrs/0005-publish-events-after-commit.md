# ADR 0005: Publish interaction events after database commit

**In short:** In the context of recording customer interactions and publishing 
Kafka events, facing the risk of the database and Kafka getting out of sync, we 
decided to publish `CustomerInteractionRecordedV1` after the database transaction 
commits instead of using a transactional outbox, to prevent events for rolled-back
interactions without introducing the additional complexity of an outbox, accepting
a small failure window between the database commit and Kafka publish.

## Context and Problem Statement

Recording an interaction writes to PostgreSQL and publishes a Kafka event. We 
need to prevent Kafka from receiving an event for an interaction that later rolls
back. When should the event be published?

## Decision Drivers

- PostgreSQL is the authoritative store for interactions
- Rolled-back interactions must not produce Kafka events
- Successful interactions should produce one event publication attempt


## Considered Options

1. Publish after database commit.
2. Use a transactional outbox.

## Decision Outcome  

Chosen option: Publish after database commit, because it prevents events from being 
published for rolled-back interactions without adding the extra persistence and 
processing required by a transactional outbox.

The flow is:

save interaction -> database commit -> publish `CustomerInteractionRecordedV1`

If the database transaction rolls back, no event is published.

If the database commits but Kafka publication fails, the interaction remains stored
in PostgreSQL and the publish failure is logged.

### Consequences

- Good, because rolled-back interactions cannot produce Kafka events
- Good, because the implementation is simpler than a transactional outbox
- Bad, because Kafka publication can still fail after the database has successfully committed
- Bad, because a committed interaction could exist without its corresponding event

### Confirmation

- Tests confirm a rolled-back transaction produces no event.
- Tests confirm a successful transaction triggers one event publication attempt.
- Tests confirm a Kafka failure does not roll back the committed interaction.
- CI verification passes before the publisher changes are merged.

## More Information

- Revisit if: guaranteed event delivery after a successful database commit becomes required
- Confidence: high
- Links: ADR 0003, Kafka publisher issue, messaging section of `docs/architecture.md`
