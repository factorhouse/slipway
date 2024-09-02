# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/)

## [1.1.16] - 2024-09-02

Bump to latest Jetty version (11.0.23 or equivalent)

## [1.1.15] - 2024-07-24

Bump to latest Jetty version (11.0.22 or equivalent)

## [1.1.14] - 2024-06-04

Bump to latest Jetty version (11.0.21 or equivalent)

## [1.1.13] - 2024-02-23

Bump to latest Jetty9 sponsored support version (9.4.54.v20240208) other Jetty versions remain the same at 11.0.20 or equivalent.

## [1.1.12] - 2024-02-05

Bump to latest Jetty version (11.0.20 or equivalent)

## [1.1.11] - 2024-01-08

Bump to latest Jetty version (11.0.19 or equivalent)

## [1.1.10] - 2023-11-01

Bump to latest Jetty version (11.0.18 or equivalent)

## [1.1.9] - 2023-10-16

Bump to latest Jetty version (11.0.17 or equivalent)

## [1.1.8] - 2023-09-05

- Bump to latest Jetty version (11.0.16 or equivalent)

## [1.1.7] - 2023-04-27

- Bump to latest Jetty version (11.0.15 or equivalent)

## [1.1.6] - 2023-03-02

- Bump to latest Jetty version (11.0.14 or equivalent)

## [1.1.5] - 2022-12-16

- Bump to latest Jetty version (11.0.13 or equivalent)
- Update readme.md

## [1.1.4] - 2022-12-06

- Rename slipway.auth to slipway.security
- Improve startup logging
- Permit configurable IdentityService
- Minor reflection fix

## [1.1.3] - 2022-12-02

- Improve websocket config logging on startup
- Rename authz namespace to auth

## [1.1.2] - 2022-11-30
### Changed
- Remove explicit defaulting of Gzip content types
- Minor auth comment and realm configuration change
- Bump Jetty9 deps to latest

## [1.1.1] - 2022-11-21
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
