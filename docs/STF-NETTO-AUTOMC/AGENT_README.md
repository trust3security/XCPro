
# AI Agent Guide for STF / Netto / Auto-MC

This folder is the source of truth for the STF / glide-netto / Auto-MC work.
Future Codex agents must read and follow the files below before making changes.

Required global rules (read first, in this order):
1) C:\Users\Asus\AndroidStudioProjects\XCPro\ARCHITECTURE.md
2) C:\Users\Asus\AndroidStudioProjects\XCPro\CODING_RULES.md
3) C:\Users\Asus\AndroidStudioProjects\XCPro\CONTRIBUTING.md
4) C:\Users\Asus\AndroidStudioProjects\XCPro\README.md (pipeline and entrypoints)

Folder files (read in this order):
1) GLIDE_NETTO_AND_AUTO_MC_SPEC.md
   - Defines glide-netto and Auto-MC semantics, gating, IAS usage, and confidence decay.
   - Treat as a contract. Do not invent behavior outside this spec.

2) SPEED_TO_FLY_SPEC.md
   - Defines speed-to-fly inputs, MC_eff rules, numeric search, smoothing, and safety bounds.
   - Must use derived active IAS bounds and the active polar in IAS.

3) XC_PRO_EXECUTION_ORDER.md
   - Mandatory dependency order: Wind -> TAS/IAS -> Glide-netto -> Auto-MC -> STF.
   - Stop and fix upstream issues before moving downstream.

4) NettoAutoMC_Implementation_Plan.md
   - Approved minimal v1 scope, SSOT ownership, and required tests.
   - Any non-trivial refactor must update this plan (CODING_RULES.md section 15A).

Implementation notes:
- Keep source files ASCII only.
- Use monotonic time in domain logic and replay time in replay mode.
- Do not add UI or ViewModel business logic; keep domain math in use cases.
- Glide-netto is separate from existing netto; show NO WIND / NO POLAR when missing.

If any ambiguity remains, stop and ask the user before implementing.

