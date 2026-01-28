# REFACTOR_SNAIL_TRAIL_OVERLAY

Goal: Split SnailTrailOverlay into pure planning/building helpers and a thin MapLibre adapter.

Phases

Phase 1 - Render plan extraction
- Add SnailTrailRenderPlanner to compute filtered points, minTime, ranges, widths, and distance thresholds.
- Add MetersPerPixelProvider interface and MapLibre adapter.
- Update SnailTrailOverlay.render to use the plan output.
- Tests: SnailTrailRenderPlannerTest (OFF, invalid location, replay distance cap, tail append, scaled lines).
- Run: ./gradlew testDebugUnitTest lintDebug assembleDebug

Phase 2 - Segment builder extraction
- Add SnailTrailSegmentBuilder (pure) that builds line/dot segments and log entries.
- Add segment data models (line, dot, log entry).
- Update SnailTrailOverlay.render to use builder and convert segments to GeoJson.
- Tests: SnailTrailSegmentBuilderTest (altitude line, vario dots, dots+lines, bounds skip).
- Run: ./gradlew testDebugUnitTest lintDebug assembleDebug

Phase 3 - Tail builder extraction
- Add SnailTrailTailBuilder (pure) with TailClipper interface and MapLibre adapter.
- Update SnailTrailOverlay.renderTail and renderTailInternal to use tail builder.
- Tests: SnailTrailTailBuilderTest (short segment null, clipper null, width selection, offset behavior).
- Run: ./gradlew testDebugUnitTest lintDebug assembleDebug

Phase 4 - Logging consolidation
- Add SnailTrailLogger to keep verbose logging out of core logic.
- Update SnailTrailOverlay to route logs through logger.
- Tests: existing tests only.
- Run: ./gradlew testDebugUnitTest lintDebug assembleDebug
