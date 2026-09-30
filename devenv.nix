{
  pkgs,
  lib,
  config,
  inputs,
  ...
}:

{
  # https://devenv.sh/basics/
  env.GREET = "devenv";

  # https://devenv.sh/packages/
  packages = [
    pkgs.quarkus
    pkgs.openspec
    pkgs.rtk
    ];

  # https://devenv.sh/languages/
  # GraalVM CE (Java 25 LTS) inkl. native-image: ermoeglicht native Kompilierung.
  languages.java = {
    enable = true;
    jdk.package = pkgs.graalvmPackages.graalvm-ce;
    maven.enable = true;
  };

  # https://devenv.sh/services/
  # OTel-Collector: OTLP via gRPC (4317) und HTTP (4318), Ausgabe via Debug-Exporter.
  services.opentelemetry-collector = {
    enable = true;
    configFile = ./otelcol/config.yaml;
  };
  # WireMock: Port 8089, Stubs aus wiremock/mappings (pro Stub eine JSON-Datei).
  services.wiremock = {
    enable = true;
    port = 8089;
    rootDir = config.devenv.root + "/wiremock";
  };

  services.postgres = {
    enable = true;
    listen_addresses = "127.0.0.1";
    initialDatabases = [
      {
        name = "durchlauferhitzer";
        user = "durchlauferhitzer";
        pass = "durchlauferhitzer";
      }
    ];
  };
  services.kafka.enable = true;
  services.opensearch.enable = true;

  # https://devenv.sh/scripts/
  scripts.hello.exec = ''
    echo hello from $GREET
  '';

  # https://devenv.sh/basics/
  enterShell = ''
    hello         # Run scripts directly
    git --version # Use packages
  '';

  # https://devenv.sh/tasks/
  # tasks = {
  #   "myproj:setup".exec = "mytool build";
  #   "devenv:enterShell".after = [ "myproj:setup" ];
  # };

  # https://devenv.sh/tests/
  enterTest = ''
    echo "Running tests"
    git --version | grep --color=auto "${pkgs.git.version}"
  '';

  # https://devenv.sh/git-hooks/
  # git-hooks.hooks.shellcheck.enable = true;

  # See full reference at https://devenv.sh/reference/options/
}
