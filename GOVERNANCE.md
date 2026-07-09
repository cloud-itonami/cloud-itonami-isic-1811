# Governance

`cloud-itonami-1811` is an OSS open-business blueprint for community
printing operations, robotics-premised.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a robot action the governor refuses is never dispatched to hardware.
- the Printing Governor remains independent of the advisor.
- hard policy violations (a print-run release without a completed
  quality inspection, a color/specification change outside verified
  scope) cannot be overridden by human approval.
- every production step, sign-off and release path is auditable.
- sensitive print-job and customer data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model, storage contract, public business model, operator certification or license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a separate trust mark and should require security, robot-safety, audit and data-flow review.

Certified operators can lose certification for:
- bypassing robot-safety or color/specification-scope checks
- mishandling print-job or customer data
- misrepresenting certification status
- failing to respond to safety incidents
