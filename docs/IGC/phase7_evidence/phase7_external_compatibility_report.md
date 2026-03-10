# External Compatibility Report

| Fixture | Parser | Parser Status | Record Count | Error |
| --- | --- | --- | --- | --- |
| production_real | aerofiles | WARN | 18969 | type |
| production_real | igc_parser | PASS | 18969 |  |
| invalid_a_not_first | aerofiles | PASS | 3 |  |
| invalid_a_not_first | igc_parser | PASS | 3 |  |
| invalid_malformed_b_short | aerofiles | WARN | 2 | type |
| invalid_malformed_b_short | igc_parser | FAIL | InvalidTrackPointLine:  | InvalidTrackPointLine |
| invalid_bad_time | aerofiles | WARN | 2 | type |
| invalid_bad_time | igc_parser | FAIL | InvalidTrackPointLine:  | InvalidTrackPointLine |
