package org.kore.durchlauferhitzer.service;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Bootstrap der Quarkus-Anwendung Durchlauferhitzer.
 */
@QuarkusMain
public class Application {

    public static void main(String... args) {
        Quarkus.run(args);
    }
}
