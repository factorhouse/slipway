# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [1 1 1] - 2022-11-21
### Changed
- Alignment of default configuration with previous versions

## [1.1.0] - 2022-09-27
### Changed
- Project structure reconfiguration and refactoring pre-public availability

## [1.0.7] - 2022-07-14
### Changed
- Bump Jetty versions to resolve CVE-2022-2047
 
## [1.0.6] - 2022-06-09
### Fixed
- Set ContextPath to `/` instead of `/*` to stop unnecessary logging of `o.e.j.s.ServletContextHandler@3ed292ae{/,null,STOPPED} contextPath ends with /*`

## [1.0.5] - 2022-04-08
### Changed
- SessionHandler: prefer `:session` key over `:cookie`

## [1.0.4] - 2022-04-08
### Changed
- Update exception for misconfigured hash login

## [1.0.3] - 2022-04-08
### Changed
- Add configuration option `:gzip-min-size`
- Add `:cookie` option to configure `org.eclipse.jetty.server.session.SessionHandler` auth handler

## [1.0.2] - 2022-04-07
### Changed
- **Jetty9** - pass in response body in the case of WS upgrade error.

## [1.0.1] - 2022-04-07
### Fixed
- **Jetty9** - better handle edge cases of upgrading WebSockets.

## [1.0.0] - 2022-04-06
### Added
- Initial release.
