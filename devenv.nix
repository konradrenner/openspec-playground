{
  pkgs,
  lib,
  config,
  inputs,
  ...
}:

# Trace-Viewer: moderner Zipkin-Server (3.x, in-memory, UI auf :9411).
# Das nixpkgs-Zipkin (1.28) spricht nur die alte v1-API, der OTel-Collector
# exportiert aber v2 - daher der exec-JAR aus Maven Central per fetchurl.
let
  zipkin-server = pkgs.fetchurl {
    url = "https://repo1.maven.org/maven2/io/zipkin/zipkin-server/3.6.1/zipkin-server-3.6.1-exec.jar";
    sha256 = "18al0rlghwqbm7cq5ix7g0jya1j7an4gca0jm3dmaf7lsh76wcnq";
  };
in
{
  # https://devenv.sh/basics/
  env.GREET = "devenv";

  # https://devenv.sh/packages/
  packages = [
    pkgs.quarkus
    pkgs.openspec
    pkgs.rtk
    pkgs.curl
    pkgs.jq
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
        name = "raumschiffwerft";
        user = "raumschiffwerft";
        pass = "raumschiffwerft";
      }
    ];
  };
  services.kafka.enable = true;
  services.opensearch.enable = true;

  # Trace-Viewer: Zipkin (in-memory, UI auf http://localhost:9411).
  # Der OTel-Collector exportiert die Traces dorthin (otelcol/config.yaml).
  processes.zipkin.exec = "${pkgs.jdk}/bin/java -jar ${zipkin-server}";

  # https://devenv.sh/scripts/
  scripts.hello.exec = ''
    echo hello from $GREET
  '';

  # End-to-End-Simulation gegen die devenv-Dienste:
  #   devenv up -d && e2e
  scripts.e2e.exec = "bash $DEVENV_ROOT/scripts/e2e.sh";

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
