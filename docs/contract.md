# API Contract

## API Endpoints
| Endpoint | Method | Description | Accepts | Returns | Status |
|----------|--------|-------------|---------|---------|--------|
| `/api/v1/customers` | GET | Retrieve a list of all customers | Page and Sorting | List of customers | 200 OK |
| `/api/v1/customers/{id}` | GET | Retrieve a specific customer by ID | customer public_id | Specific customer | 200 OK |
| `/api/v1/customers/{id}/interactions` | GET | Retrieve interactions for a specific customer | customer public_id | List of interactions | 200 OK |
| `/api/v1/customers/{id}/interactions` | POST | Create a new interaction for a specific customer | customer public_id and interaction request | Created interaction | 201 Created |
| **Optional for Capstone Slice** |---|---|---|---|---|
| `/api/v1/customers` | POST | Create a new customer | customer create request | Created customer | 201 Created |
| `/api/v1/customers/{id}` | PUT | Update an existing customer | customer public_id and updated data | Updated customer | 200 OK |
| `/api/v1/customers/{id}` | DELETE | Delete a specific customer by ID | customer public_id | Deleted customer | 200 OK |