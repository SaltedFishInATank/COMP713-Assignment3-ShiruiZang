# COMP713 Assessment 3 — final silent demonstration verification

Student: Shirui Zang (21136179)  
Project: Fieldwork

The completed video is submitted **separately from the source-code ZIP** as `COMP713_Assessment3_Demonstration_Subtitled_ShiruiZang_21136179.mp4`.

| Check | Verified final video |
|---|---|
| Length | 625.233 seconds (10:25), below the 12-minute maximum |
| Resolution | 1920 x 1080 |
| Codec | H.264 |
| Playback cadence | Approximately 15 distinct frames/sec after timestamp restoration |
| Narration/audio | No audio stream |
| Captions | English text burned into the frames |
| SHA-256 | `fec4fe4f6a5fb28d48211c3e26874c3b7fd5b0ff131eb45f6ca4b5847b03b5d8` |

## Editing reconciliation

A previous verification note described an older 625.328-second demonstration video with synthetic spoken narration. The uploaded ZIP instead contained a 312.634-second video with English subtitles and effectively silent audio; this is almost exactly a 2x-speed edit. The corrected final video restores playback timestamps to about 10:25, preserves the visual content and embedded subtitles, and removes its audio stream.

This is a timing correction, not a recreation of tasks, Payara logs or test results. Existing source execution evidence remains in `evidence/`.

## Reviewed representative scenes

Frames show student/project introduction, Payara Tools deployment, coordinator browser task creation, native Android worker acceptance and submission, WebSocket-updated coordinator review, OpenMQ retry/deduplication records, server recovery/persistence and test results.

The recorded Payara Tools deployment reflects the prepared isolated VS Code extension profile. The student's separate manual registration failure in their normal VS Code instance is not represented as solved.

## Limitations

The student should watch the entire corrected video for subtitle readability. This pass did not rerun the original Payara/Android tests and does not verify three weeks of development commits or approval for a solo submission.
