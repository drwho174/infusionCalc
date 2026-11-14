# Propofol TCI

A calculator for infusion rate from a target concentration: you enter the
target propofol concentration (Cp — plasma or Ce — effect site), the model and
the patient data, and the app calculates the bolus, maintenance rates, the
predicted Cp/Ce over time and the pump settings for a manual syringe pump.

## Marsh and Schnider models

A brief reference for the propofol pharmacokinetic models used by the TCI card.
Detailed formulas and calculation rules — in `docs/SPEC-TCI.md` §5.2, §6.

### Marsh

| Parameter | Formula |
|---|---|
| `V1` | `0.228 × weight` |
| `V2` | `0.4643 × weight` |
| `V3` | `2.895 × weight` |
| `CL1` | `0.027 × weight` |
| `CL2` | `0.0255 × weight` |
| `CL3` | `0.0095 × weight` |
| `ke0` | `0.26 min⁻¹` |

- Only **weight** is required.
- Age, height and sex do not affect the model.
- Simple and widely used; convenient when anthropometry is unavailable.
- No formal BMI limit, but prediction accuracy decreases in marked obesity.

### Schnider

| Parameter | Formula |
|---|---|
| `V1` | `4.27 L` (fixed) |
| `V2` | `18.9 − 0.391 × (age − 53)` |
| `V3` | `238 L` |
| `CL1` | `1.89 + 0.0456 × (weight − 77) − 0.0681 × (LBM − 59) + 0.0264 × (height − 177)` |
| `CL2` | `1.29 − 0.024 × (age − 53)` |
| `CL3` | `0.836` |
| `ke0` | `0.456 min⁻¹` |

- Requires **weight, height, age and sex**.
- `LBM` (lean body mass, James formula):
  - men: `1.10 × weight − 128 × (weight / height)²`
  - women: `1.07 × weight − 148 × (weight / height)²`
- Accounts for age, sex and height; `ke0` is higher, so Ce equilibrates with
  plasma faster.
- BMI limit: **men < 42**, **women < 35 kg/m²**. Outside this range the app
  warns that the model may have a noticeable error.

## Differences

| | Marsh | Schnider |
|---|---|---|
| Patient data | weight | weight, height, age, sex |
| `V1` | weight-dependent | fixed |
| Age/sex effect | no | yes |
| `ke0` | 0.26 | 0.456 |
| BMI limit | none | < 42 (M) / < 35 (F) |
| Convenient when | only weight is known | full anthropometry is available |

## What the app calculates

- **Bolus:** `dose = target concentration × V1`, volume = dose / drug
  concentration, time — at the configured bolus rate.
- **Initial maintenance rate:** `R0 = C × (CL1 + CL2 + CL3)`.
- **Steady-state rate:** `R∞ = C × CL1`.
- **Cp/Ce over time** — stepwise simulation; maintenance steps are calculated
  for a manual pump (5 minutes by default, adjustable in TCI settings).

## Which model to choose

- **Schnider** — when height, age and sex are available and BMI is within the
  allowed range: the model accounts for more factors.
- **Marsh** — when only weight is known or BMI is outside the Schnider limits
  (understanding that the prediction is less individualised).
- Both models are **population** models: the prediction is not a measured
  concentration.

## Limitations

- **Adults only**, anaesthesia only; not for sedation or children.
- Concomitant drugs, haemodynamics, hepatic/renal function and individual
  sensitivity are not accounted for.
- In **Ce** mode the bolus uses the same `C × V1` formula; this is a
  simplification — rely on the predicted Ce.
- The app does not control the pump and does not replace clinical assessment of
  anaesthetic depth and monitoring.

## References

- Marsh B. et al. Pharmacokinetic model driven infusion of propofol in children
  and adults. *Br J Anaesth*. 1991;67:41–48.
- Schnider T.W. et al. The influence of method of administration and covariates
  on the pharmacokinetics of propofol in adult volunteers.
  *Anesthesiology*. 1998;88:1170–1182.
- Schnider T.W. et al. The influence of age on propofol pharmacodynamics.
  *Anesthesiology*. 1999;90:1502–1516.
- Propofol summary of product characteristics (SmPC).
