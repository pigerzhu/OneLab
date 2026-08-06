# GPU Frequency Range Experiment Design

## Goal

Verify whether OneLab can hold a persistent GPU minimum and maximum frequency range on
the connected Snapdragon Fold6 without polling sysfs or keeping the OneLab application
process alive.

This is an isolated experimental feature. It must not modify the existing SDHMS GPU cap
control or appear on a stable feature page until both DVFS directions are confirmed on
the device.

## Confirmed Evidence

- SDHMS implements its thermal GPU limiter through `GPUFreqMax` and
  `SemDvfsManager` type `17`.
- The device exposes 16 GPU frequencies from 80 MHz through 1000 MHz.
- KGSL exposes `min_clock_mhz` and `max_clock_mhz`, but the observed minimum changed
  from 422 MHz to 231 MHz without OneLab writing it. A one-shot sysfs write therefore
  cannot provide a persistent range.
- Samsung's candidate GPU minimum DVFS request is type `16`; this remains runtime
  evidence to obtain rather than an assumed production contract.

## Architecture

The prototype runs inside the already scoped and long-lived SDHMS process. A dedicated
GPU range controller owns two `SemDvfsManager` objects:

- minimum-frequency vote: candidate type `16`;
- maximum-frequency vote: verified type `17`.

The controller reads an immutable settings snapshot. A `ContentObserver` reloads the
snapshot when the experiment switch or either frequency changes. No settings, disk,
shell, or Binder work occurs in a render or performance hot path.

The normal OneLab process only displays controls and writes the shared settings
contract. It does not retain a background service.

## User Interface

The prototype appears only under Labs and contains:

- an experiment enable switch;
- a discrete minimum-frequency selector;
- a discrete maximum-frequency selector;
- a compact status showing requested range and whether the runtime controller reported
  acquisition success.

Both selectors use frequencies reported by the current verified device table. The
minimum may not exceed the maximum. Equal endpoints represent a lock-frequency request.
Values are committed when interaction ends rather than continuously while dragging.

The existing SDHMS GPU cap slider remains unchanged because it controls a different
contract: the lowest thermal maximum cap that OneLab allows SDHMS to request.

## Runtime Behavior

When enabled, the controller:

1. validates and normalizes both endpoints to supported frequencies;
2. creates or reuses the minimum and maximum DVFS managers;
3. applies each value and acquires both requests;
4. publishes a compact success or failure status for the Labs UI and diagnostics.

When a value changes, the controller releases the old request before acquiring the new
one. When disabled, when validation fails, or when the SDHMS process shuts down, it
releases every request it owns. Exceptions fail open and preserve Samsung's behavior.

The experiment must not write KGSL sysfs nodes, run a periodic enforcement loop, change
Scene configuration, or intercept unrelated DVFS clients.

## Verification

The experimental APK is installed only to Android user 0. Installation does not reboot
the phone. After the user reboots, verification reads, but does not otherwise modify,
the following runtime evidence:

- LSPosed log confirms that the isolated GPU range controller installed;
- type `16` exposes supported GPU frequencies and accepts `acquire()`;
- type `17` accepts the selected maximum;
- KGSL effective minimum and maximum match the requested range;
- GPU current frequency remains within the range under idle and load;
- changing SDHMS or Scene policy does not silently remove OneLab's active vote;
- disabling the experiment releases both votes and restores the previous effective
  constraints.

The test must include an equal-endpoint lock request, but only at a conservative
frequency and for a short verification interval.

## Stop Conditions

Stop and remove the prototype rather than adding a fallback if any of these occur:

- type `16` is unavailable or does not represent a persistent GPU minimum vote;
- acquiring either request crashes or destabilizes SDHMS;
- the effective limits require periodic sysfs rewriting;
- disabling the feature cannot reliably restore the prior constraints;
- the feature overrides thermal emergency protection rather than composing with it;
- it causes a persistent conflict with Scene or another active scheduler.

Only successful on-device evidence can promote this experiment into a user-facing
feature. Static class availability, hook installation, and stored settings are not proof
that the GPU range is enforced.
