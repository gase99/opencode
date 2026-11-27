# Operator Profiles & Ranking Honesty

## What the app can and cannot know

ConfigHub runs on **one** phone attached to **one** network. Therefore:

* ✅ It can measure DNS/TCP/TLS latency **on the current network** and label the
  sample with the current operator (PLMN) and network type (Wi-Fi/Mobile).
* ❌ It can NOT measure latency "for MCI" while the phone is on Irancell. The app
  never presents data as if it came from another operator.

The UI shows, for every measurement:
`operator · network type · latency · "measured on current network" · timestamp`.

## Detection

* Auto: `TelephonyManager.getNetworkOperator()` (no permission required) →
  PLMN mapping below. This reflects the registered/SIM operator; on Wi-Fi it
  typically reflects the SIM.
* Manual: the user picks a profile in Settings (MCI, Irancell, Rightel, Other).

| Operator | PLMN codes |
| --- | --- |
| MCI / Hamrah-e Aval | 432-11, 432-19 |
| Irancell (MTN) | 432-35, 432-32 |
| Rightel | 432-20 |
| Other / Unknown | fallback |

## Per-operator profiles

Each operator has **editable** thresholds:

* `goodBelowMs` — color band "good"
* `acceptableBelowMs` — color band "acceptable"

and **optional** scoring-weight overrides. Defaults are conservative estimates for
UI banding only — they are clearly labeled as defaults in Settings and are NOT
presented as measurements. Users should tune them to their own experience.

## Score integration

`ScoreEngine` combines latency, jitter, failure rate (history), recency of last
success, and an optional protocol component with normalized weights. Operator
thresholds affect banding; operator weight overrides (when set) affect ranking.
Neither fabricates latency data — untested configs show "Not tested" and are ranked
by cached scores only.
