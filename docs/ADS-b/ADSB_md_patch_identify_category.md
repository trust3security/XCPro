# ADSB_md_patch_identify_category.md
(UPDATED: category 0/1 is normal, add inference fallback)

Add these requirements to your ADSB docs / tasks:

1) Request OpenSky `/states/all` with `extended=1`
2) Parse category at index 17 using Number->Int conversion
3) Log row.size (must be 18 to have category)
4) Display raw category label + integer in the details sheet
5) Implement inference fallback for category 0/1/null:
   - use speed/altitude/on_ground thresholds
   - label as INFERRED
