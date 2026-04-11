<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# edgejs-jetbrains Changelog

## [Unreleased]

## [0.0.4] - 2026-04-11
### Changed
- Replaced the Edge file icon with the new SVG artwork and packaged the same icon as the plugin logo for Marketplace and the IDE plugins panel.

## [0.0.3] - 2026-04-11
### Fixed
- Fixed JavaScript injection for Edge directives with multiple arguments, so commas are treated as argument separators instead of the JavaScript comma operator.

## [0.0.2] - 2026-04-10
### Added
- Added a dedicated file icon for `.edge` templates.

### Fixed
- Fixed directive argument highlighting so leading identifiers in directives like `@if(...)`, `@let(...)`, and `@each(...)` no longer render their first character with a different color.

## [0.0.1] - 2026-04-08
### Added
- Initial public release of `edgejs-jetbrains` for JetBrains IDEs.
- Syntax highlighting for AdonisJS Edge `.edge` templates.
- JavaScript injection inside Edge expressions and directive arguments.
- Navigation from `@include(...)` paths and component tags to templates under `resources/views/**`.
