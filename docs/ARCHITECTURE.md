# Architecture

Gradle subprojects are intentionally limited to four: `ShimePlugin`, `ShimePluginLibrary`, `ShimePluginCommand`, and `ShimePluginNMS`.

GUI, Config, Database and other general-purpose APIs belong inside the `ShimePluginLibrary` module as packages; they are not separate Gradle subprojects. Packet support belongs inside `ShimePluginNMS`.
