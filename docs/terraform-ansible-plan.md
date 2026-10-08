# Terraform / Ansible plan

## Scope

Use Terraform for the application's networking and persistent storage, and Ansible for its configuration.
Both run through GitHub Actions against the instructor-supplied Kubernetes namespace.

Terraform uses the `hashicorp/kubernetes` provider to define NetworkPolicies and PVCs in `infra/terraform/`.
Ansible uses `kubernetes.core.k8s` in `infra/ansible/configure.yml` to apply non-secret ConfigMaps.
Deployments, Services, Ingress and runtime Secrets are applied by the release workflow. Database schema changes
stay in Flyway migrations. Keep these definitions separate so one apply does not overwrite another's changes.

The application scope excludes namespace creation, cluster quotas, nodes, storage classes and managed cloud services.
S3 is used only for remote Terraform state.
PostgreSQL remains on the internal network; it is not exposed through a public Service or Ingress.

Use local and CI environments for static validation and an isolated rehearsal namespace for changes that need a
running cluster. Staging and the production-like demo must have separate namespace credentials, configuration and
Terraform state. A second inventory pointing at the same namespace does not provide that separation.
The [environment strategy](environment-strategy.md) defines promotion between environments.

## Terraform state

Store state in a private Amazon S3 bucket with encryption, TLS, bucket versioning and S3 locking enabled through
`use_lockfile = true`. Use a separate state key for each environment and pin a Terraform version that supports
S3 lockfiles. Provision the backend and its access policy in the authorized training environment before running
`terraform init` against it.

Limit access to the environment's state key and lockfile. Include permission to delete the lockfile so Terraform
can release a lock. Supply credentials at runtime; do not put them in HCL or pass them through `-backend-config`.
Commit non-secret backend settings and `.terraform.lock.hcl` to make the configuration reproducible.

Keep state, saved plans, variable files and crash output out of Git and public Actions artifacts. Publish only
scrubbed plan summaries. `sensitive = true` hides values in normal output but does not remove them from state or
saved plans. Restrict access to the backend, retain previous state versions for recovery, and never edit state by
hand or force-unlock a running job. These controls follow
[HashiCorp's S3 backend documentation](https://developer.hashicorp.com/terraform/language/backend/s3) and
[sensitive-data guidance](https://developer.hashicorp.com/terraform/language/manage-sensitive-data).

## Ansible configuration

Pin the `kubernetes.core` collection. Pass the environment, cluster context and namespace explicitly, and stop the
playbook if any is missing or does not match the selected target. Supply credentials through scoped GitHub
Environment secrets or an approved secret store. If using Ansible Vault, keep its password outside Git.

Apply `crm-api-config` with `kubernetes.core.k8s`, `state: present` and a fixed set of non-secret values. Running the
same playbook again should leave the ConfigMap unchanged. Avoid timestamps, random values and shell commands that
report a change on every run.

Check syntax, run check mode, then apply the playbook twice in the rehearsal environment. The second run must
report `changed=0`, `failed=0` and `unreachable=0`. Keep both recaps as the idempotence check.

Check mode previews changes; it is not an idempotence test and does not automatically reject drift. Review the
non-secret diff and explicitly fail the validation stage on unexpected changes. Use modules that support check
mode and do not force tasks to run with `check_mode: false`. See the
[k8s module](https://docs.ansible.com/projects/ansible/latest/collections/kubernetes/core/k8s_module.html) and
[Ansible check-mode documentation](https://docs.ansible.com/projects/ansible/latest/playbook_guide/playbooks_checkmode.html).

Do not place secret values in ConfigMaps. Runtime Secrets are injected during release from the environment's
secret store. Protect any Ansible task that handles sensitive values with `no_log: true` and `diff: false`, and
never print them through debug tasks or registered results.
[Ansible's logging guidance](https://docs.ansible.com/projects/ansible/latest/reference_appendices/logging.html)
explains the limits of `no_log`.

## Validation and release

1. **Validate the PR.** Run `terraform fmt -check -recursive`, `terraform init -backend=false`,
   `terraform validate` and `ansible-playbook --syntax-check`. PR validation has no state or cluster credentials.
2. **Prepare the plan.** In a trusted job, check the target and permissions, initialize the remote backend and run
   `terraform plan -out=tfplan`. Run Ansible check mode and inspect the non-secret diff. Keep the saved plan private.
3. **Review and approve.** Review resource additions, changes and deletions, storage, quota and secret handling.
   Record approval against the commit, environment and saved plan. Generate a new plan after changes or drift.
4. **Apply and deploy.** After approval, apply the saved Terraform plan, run Ansible configuration and deploy the
   image digest already verified by CI. Do not rebuild the application during deployment. A failed stage stops
   subsequent stages; inspect partial changes before retrying.
5. **Verify and record.** Check rollout and readiness, run authenticated and denied-path smoke tests, and retain
   scrubbed plan summaries, Ansible recaps, commit/run IDs, image digests and rollback results in the
   [runbook](capstone-cicd-runbook.md) and [evidence index](../defense/evidence-index.md).

Do not use `terraform destroy`, delete PVCs or reset the shared database as routine cleanup. Rolling back an image
does not reverse infrastructure or schema changes. Review compatibility with the previous application version
before release; destructive changes require instructor approval and a recovery plan.

## Comparison with the capstone instructions

| Requirement | How this plan addresses it |
| --- | --- |
| Lab 48 Step 5: scope, providers, remote state and environment boundaries. | Defines namespaced networking/storage and configuration, names the provider and Ansible collection, and separates state and credentials by environment. |
| Module 48 PDF pp.22, 24, 26: reviewed plans, locked state, idempotence and no secrets in Git. | Uses encrypted, versioned S3 state with locking, reviews saved plans, requires a zero-change second Ansible run and injects secrets at runtime. |
| Lab 51 Step 4 and Module 51 PDF pp.29, 31, 34: Terraform plan, approved apply and Ansible syntax/check stages. | Places validation, plan, review and apply before deployment; promotes the verified image digest without rebuilding it. |
| Capstone brief: infrastructure is scoped, with automation evidence as assigned. | Limits automation to authorized namespace resources and records validation, configuration and release evidence. |
| Module 52 PDF p.31: claims must point to reproducible artifacts. | Saves the plan summary, run recaps and release identity in the evidence index. |

This plan targets the supplied k3s cluster, using a Kubernetes namespace and Ingress. Adel confirmed k3s on
2026-10-08 (R-02 in the [risk register](risk-register.md)).

Sources: [Lab 48 Step 5](https://github.com/Innovation-In-Software/bc-sw-engineer-java-angular-participant/blob/f87d5852c78b8b13c67bab7470d17cff3d5fa5e9/labs/Week%206%20-%20Capstone%20Project/module-48/lab48/LAB-48-GUIDE.md#step-5--terraform--ansible-and-environment-strategy),
[Lab 51 Step 4](https://github.com/Innovation-In-Software/bc-sw-engineer-java-angular-participant/blob/f87d5852c78b8b13c67bab7470d17cff3d5fa5e9/labs/Week%206%20-%20Capstone%20Project/module-51/lab51/LAB-51-GUIDE.md#step-4--terraformansible-stages-and-openshift-deploy),
and the [capstone brief](https://github.com/Innovation-In-Software/bc-sw-engineer-java-angular-participant/blob/f87d5852c78b8b13c67bab7470d17cff3d5fa5e9/labs/Week%206%20-%20Capstone%20Project/CAPSTONE-BRIEF-AND-RUBRIC.md).
