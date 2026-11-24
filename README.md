# BastionMCCommands 1.0.0
Proyecto Java 21 para Purpur/Paper 1.21.11. Compilar con `mvn clean package` o subir los contenidos (incluido .github) a GitHub y usar Actions > Build BastionMCCommands. Descargar el artifact BastionMCCommands-JAR, extraer y colocar el JAR en plugins. Reiniciar. No requiere cambiar otros plugins.

## Administración
- `/bcmd menu` selecciona grupo y abre lista paginada de comandos registrados. Clic alterna la regla `comando *` del grupo; reglas parciales permanecen. El amarillo indica rutas parciales. La GUI no concede permisos de los plugins ni anula deny.
- `/bcmd add default comando *` permite comando y argumentos.
- `/bcmd add default ah sell *` permite esa rama y argumentos.
- `/bcmd remove default comando *` elimina exactamente la regla indicada.
- `/bcmd list default` muestra reglas del grupo.
- `/bcmd reload` recarga YAML; si es inválido mantiene la configuración anterior.
- `/lp group vip permission set bastioncommands.group.vip true` suma las reglas VIP al default. Para otros rangos, crear el grupo en YAML y otorgar `bastioncommands.group.<grupo>`.
- `bastioncommands.admin` permite administrar; `bastioncommands.bypass` omite la lista. OP tiene bypass por defecto. No otorgar estos permisos a jugadores normales. Para desactivar bypass OP, ajustar settings.op-bypass y revocar el permiso de bypass explícitamente (su default es op).

## Seguridad y comportamiento
Las reglas son rutas literales, sin distinguir mayúsculas. `ah` permite únicamente /ah; `ah sell *` permite /ah sell con o sin argumentos. deny gana frente a allow. Se bloquean los prefijos de namespace salvo excepciones configuradas. Los permisos normales siguen siendo necesarios. No autorizar raíces administrativas ni alias peligrosos. El bloqueo se aplica a comandos escritos por jugadores; consola y ejecuciones internas de plugins no están interceptadas. Algunos plugins pueden ejecutar comandos de forma indirecta: conservar siempre permisos mínimos. El TAB raíz y las ramas literales de Paper se filtran, así como sugerencias dinámicas; las ramas de argumentos genéricos del cliente pueden seguir mostrándose dependiendo del plugin. La ejecución se valida por separado. Los cambios de permisos se detectan periódicamente (2 segundos por defecto). 

## Prueba inicial
Entrar con cuenta sin OP y sin bypass: comprobar /ah, /spawn, /clan; /plugins, /pl, /bukkit:plugins, /ah admin y /minecraft:op deben bloquearse y las raíces ocultas. Comprobar `/bcmd menu` como OP y agregar/quitar un comando de prueba. Revisar todos los alias de plugins y ajustar la lista inicial antes de abrir al público. No se han realizado pruebas en un servidor real.
