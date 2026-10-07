# ADR 0002 - IDs and Errors
## Status: Accepted
## Context:
We need to establish a consistent approach for handling public customer IDs and problem details within errors.

## Decision:
For public customer IDs, we chose to use the format `CUS-XXXX`, with `XXXX` being the unique identifier for the
customer, and `CUS` being the signifier that the ID belongs to a customer.

For problem details within errors,
we will use the standardized RFC 9457 Problem Details format. This is a well known and widely used standard that
is built into Spring Boot.

## Rejected Alternatives:
For customerIds, we could have exposed the internal BIGSERIAL primary key used in the database, but this would
both expose the database internals and create a dependency on database implementation within the business
logic.

For errors, we could use Spring Boot's legacy default error body, but there is no standard associated with
that implementation.

## Consequences:
As a consequence of using formatted strings as public identifiers for customers, the internals of the database
are free to change as it likes without impacting the public API. However, there is a risk with this current
implementation that sequential customerIds could be guessed, so strict JWT authorization will be required to
ensure correct access is given to customer data retrieval.

With RFC 9457 error details, we will have a standardized and tested approach to handling errors, but we will need to
map every exception type to the appropriate problem detail structure.