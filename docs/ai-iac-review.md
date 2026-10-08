# AI IaC review

Lab 45's review record for `infra/`, `scripts/install-iac-tools.sh` and the CD `infra` job.

## Contract

- Target: the `student08` namespace on the course k3s cluster only. No namespaces, quotas, nodes, StorageClasses or
  cloud resources.
- Terraform owns the 7 NetworkPolicies and the 2 PVCs; Ansible owns `crm-api-config`. The release bundle owns everything else.
- Forbidden: public exposure of PostgreSQL or Kafka, secrets in HCL, YAML, state or logs, unpinned providers or
  collections, `terraform destroy`, deleting a PVC, applying without a reviewed plan.
- Adopting the live objects must plan as 0 to add, 0 to change, 0 to destroy.

## Prompt (summarized)

Implement docs/terraform-ansible-plan.md against the live namespace: match every live object exactly, adopt with
import blocks, pin every version, keep plans and state out of Git and public logs, and show the checks that prove it.

## Accepted

| Item | Why |
| --- | --- |
| `import` blocks kept after adoption | Re-adopt instead of AlreadyExists if state is lost |
| Restart handler stamps a config hash | Restarts only when the config changes; the second run stays at `changed=0` |
| Digest-bound apply | Applies only the plan that was reviewed, without publishing the plan file |

## Rejected or hardened

| Suggestion | Risk | Change |
| --- | --- | --- |
| S3 state backend (from the #99 plan) | Can't be built: the training AWS account is read-only for us | `kubernetes` backend: Secret `tfstate-default-crm`, Lease lock (R-14) |
| Selectors and labels without `app.kubernetes.io/part-of` | Plan: 9 to import, 6 to change (4 policies, 2 PVCs), not a clean adoption | Match the labels kustomize added; plan is 0 changes |
| Terraform-owned PVCs without a guard | A replacement would delete the database volume | `prevent_destroy` on both PVCs |
| Printing the plan in CD | Public logs would show every attribute | Address + action summary and a digest only |

## Human corrections

| File | Change | Why |
| --- | --- | --- |

## Validation evidence

Local, 2026-10-07:

- `terraform fmt -check`, `init -backend=false`, `validate`: pass. Provider `hashicorp/kubernetes` 3.3.0 locked.
- Read-only plan against the namespace (local state copy, no lock): `Plan: 9 to import, 0 to add, 0 to change, 0 to destroy`.
  Two runs gave the same change digest.
- `ingress_namespace = "student08"`: rejected by variable validation.
- `ansible-playbook --syntax-check`: pass. `ansible-lint` 26.9.0: pass at the production profile. Check mode against
  the namespace: `changed=0`. Missing `crm_context`: the assert fails the play.
- Changed ConfigMap value in check mode: task `changed`, handler fired, live ConfigMap untouched.
- Trivy 0.75.0 config: `infra/terraform` has no findings. With `ip_block { cidr = "0.0.0.0/0" }` added to
  `crm-ingress`, the gate fails on KUBE-0001 (HIGH, unrestricted ingress). The YAML scan of `openshift/` has no
  equivalent rule, so moving the policies to Terraform adds this check.

Live (CD runs):

- `infra-plan`:
- `infra-apply`, second Ansible run:
- Follow-up `infra-plan`:

## Residual risks

- R-14: state is readable by anyone with the namespace token. Owner Brandon, until 2026-10-12.
- Until the release bundle stops shipping them, the bundle and Terraform both write the same NetworkPolicies;
  don't change them in between.

## Approval

- Brandon, (date), (approved / changes requested)
