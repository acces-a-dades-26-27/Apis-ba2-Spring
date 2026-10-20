# Spring Boot · Sessió 1: el projecte i l'arquitectura en capes

**M0486 Accés a dades · M0490 BA2 Desenvolupament i consum d'API · 2n DAMVi**

Fins ara hem fet l'accés a dades amb Hibernate "a mà": `hibernate.cfg.xml`, `SessionFactory`, sessions, transaccions i un DAO fort que ho amagava tot. Avui posem **Spring Boot** a sobre. Les entitats **no canvien**: canvia qui crea els objectes, qui obre les transaccions i com s'arriba a les dades des de fora (una API web).

---

## Índex

1. [Què és Spring i què és Spring Boot](#que-es)
2. [Versions d'aquest curs i exemples antics](#versions)
3. [Crear el projecte](#crear)
4. [El `pom.xml`](#pom)
5. [Estructura de paquets](#estructura)
6. [La classe principal](#main)
7. [`application.properties`](#properties)
   - [7.1 Connexió a MySQL](#prop-connexio)
   - [7.2 `ddl-auto`](#prop-ddl)
   - [7.3 Veure l'SQL](#prop-sql)
   - [7.4 Altres propietats útils](#prop-altres)
   - [7.5 De `hibernate.cfg.xml` a `application.properties`](#prop-cfg)
   - [7.6 Noms de taules i columnes: compte!](#prop-noms)
8. [Beans i injecció de dependències](#di)
9. [L'arquitectura en capes](#capes)
   - [9.1 MVC i Front Controller](#mvc)
   - [9.2 Controlador, servei i repositori](#csr)
   - [9.3 Del DAO fort d'Hibernate a Spring](#dao-spring)
10. [Primer exemple complet: `Plataforma`](#exemple)
    - [10.1 L'entitat](#ex-entitat)
    - [10.2 El repositori](#ex-repositori)
    - [10.3 El servei](#ex-servei)
    - [10.4 El controlador](#ex-controlador)
    - [10.5 Executar i provar](#ex-executar)
11. [El recorregut d'una petició](#recorregut)
12. [Cap a Catan](#catan)
13. [Errors típics](#errors)

---

<a id="que-es"></a>
## 1. Què és Spring i què és Spring Boot

### Spring Framework

Un framework per fer aplicacions Java, sobretot de servidor. La idea central és la **inversió de control (IoC)**:

| Sense Spring | Amb Spring |
|---|---|
| El nostre codi fa `new VideojocDao()`, `new SessionFactory...` | Spring crea els objectes i els connecta entre ells |
| Cada classe decideix de qui depèn i com el crea | Cada classe **declara** de qui depèn; Spring li ho dona |
| Obrim i tanquem sessions i transaccions a mà | Una anotació (`@Transactional`) i Spring ho fa |

Els objectes que crea i gestiona Spring s'anomenen **beans**, i el lloc on viuen és el **contenidor** (o *context d'aplicació*). Ho veiem a [§8](#di).

Spring és un ecosistema de projectes. Els que fem servir:

| Projecte | Per a què |
|---|---|
| **Spring Framework** | El nucli: contenidor de beans, transaccions, Spring MVC (web) |
| **Spring Boot** | Configura i arrenca tot el que hi ha a sota amb el mínim de feina |
| **Spring Data JPA** | Repositoris: el CRUD i les consultes sense escriure implementació. Per sota fa servir JPA amb **Hibernate** |
| Spring Security, Spring Data MongoDB... | No els fem en aquest bloc |

### Spring Boot

Configurar un projecte Spring "a pèl" no és trivial: triar versions compatibles de desenes de llibreries, declarar beans, configurar el servidor web, la connexió a la BD, Hibernate... **Spring Boot fa aquesta feina per nosaltres**:

- **Starters**: una dependència (`spring-boot-starter-data-jpa`) porta tot el que cal per a una funcionalitat, amb versions que funcionen juntes.
- **Autoconfiguració**: si troba el driver de MySQL i Spring Data JPA al *classpath*, crea sol la connexió, l'`EntityManagerFactory` (l'equivalent de la `SessionFactory`) i el gestor de transaccions.
- **Servidor web incrustat**: porta un **Tomcat** dins. No cal instal·lar cap servidor; l'aplicació s'executa amb un `main` com qualsevol programa Java i escolta peticions HTTP.
- **Sense XML**: la configuració va a `application.properties` i a anotacions. No genera codi ni fitxers XML.

> Spring Boot no és un altre framework: és Spring amb la configuració ja feta. Tot el que configura automàticament ho podem canviar.

---

<a id="versions"></a>
## 2. Versions d'aquest curs i exemples antics

| Peça | Versió | Notes |
|---|---|---|
| Java | 21 (o 25) | Spring Boot 4 en demana com a mínim 17 |
| Spring Boot | **4.1.x** | La 3.5 ja no té suport gratuït des de juny de 2026 |
| Spring Framework | 7.0.x | Ve amb Spring Boot |
| Hibernate ORM | 7.x | Ve amb Spring Boot. A les sessions d'Hibernate fèiem servir la 6.6: les anotacions `jakarta.persistence` són les mateixes |
| Jackson (JSON) | 3.x | Ve amb Spring Boot |
| Maven | 3.9 o superior | O el *wrapper* `mvnw` que porta el projecte |
| IDE | Eclipse amb Spring Tools (STS), IntelliJ o VS Code | |

A Internet trobareu molts exemples de versions antigues. Les diferències que més us trobareu:

| Si veieu... | Avui és... | Des de |
|---|---|---|
| `import javax.persistence.*` | `import jakarta.persistence.*` | Spring Boot 3 |
| `repository.findOne(id)` | `repository.findById(id)` (retorna `Optional`) | Spring Data 2 |
| `repository.exists(id)` | `repository.existsById(id)` | Spring Data 2 |
| `repository.getOne(id)` / `getById(id)` | `repository.getReferenceById(id)` | Spring Data 2.7 |
| `deleteInBatch(...)` | `deleteAllInBatch(...)` | Spring Data 2.5 |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` (l'antic encara funciona, però està obsolet) | Spring Boot 4 |
| `import com.fasterxml.jackson.databind...` | `import tools.jackson.databind...` | Spring Boot 4 (Jackson 3) |
| `@JsonIgnore`, `@JsonManagedReference`... | **No canvien**: continuen a `com.fasterxml.jackson.annotation` | |
| `@MockBean` als tests | `@MockitoBean` | Spring Boot 4 |
| `WebSecurityConfigurerAdapter` | No existeix (Spring Security 6+) | Spring Boot 3 |

---

<a id="crear"></a>
## 3. Crear el projecte

Dues maneres, amb el mateix resultat:

1. **Web**: [start.spring.io](https://start.spring.io/) → *Generate* → es descarrega un zip → a Eclipse, `File > Import > Existing Maven Projects`.
2. **Eclipse amb Spring Tools**: `File > New > Spring Starter Project` (fa servir el mateix Initializr per dins).

| Camp | Valor | Per què |
|---|---|---|
| Project | **Maven** | No Gradle |
| Language | Java | |
| Spring Boot | La 4.1.x més recent (no una *SNAPSHOT* ni *M1*) | Versió estable |
| Group | `cat.iesabadell` | Prefix dels paquets |
| Artifact / Name | `videojocsSpring` | Nom del projecte |
| Package name | `cat.iesabadell.videojocs` | **El paquet arrel**: tot el codi anirà a sota ([§5](#estructura)) |
| Packaging | Jar | Amb el Tomcat incrustat no cal War |
| Java | 21 | |

Dependències:

| A l'Initializr | Starter | Per a què |
|---|---|---|
| **Spring Web** | `spring-boot-starter-webmvc` | Controladors REST, Tomcat, JSON |
| **Spring Data JPA** | `spring-boot-starter-data-jpa` | Repositoris, Hibernate, transaccions |
| **MySQL Driver** | `mysql-connector-j` | Connexió a MySQL |
| **Validation** | `spring-boot-starter-validation` | `@NotBlank`, `@Size`... (sessió 2) |
| Spring Boot DevTools (opcional) | `spring-boot-devtools` | Reinicia l'aplicació sola quan desem un canvi |

I una que no és a l'Initializr i s'afegeix a mà al `pom.xml` ([§4](#pom)):

| Dependència | Per a què |
|---|---|
| `springdoc-openapi-starter-webmvc-ui` | **Swagger UI**: una pàgina web que llista tots els endpoints i permet provar-los des del navegador, també `POST`, `PUT` i `DELETE` ([§10.5](#ex-executar)) |

Dependències que **no** cal afegir:

| Dependència | Per què no |
|---|---|
| **Spring Data JDBC** | És una alternativa a JPA, no un complement. Amb JPA ja tenim la connexió JDBC |
| **Rest Repositories** (Spring Data REST) | Publica **automàticament** un endpoint per a cada repositori (`/plataformes`, `/videojocs`...) sense passar pel nostre servei ni pel controlador. Apareixen URL que no hem escrit i que se salten les regles de negoci |

---

<a id="pom"></a>
## 4. El `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 1. EL PARE: hereta la configuració i LES VERSIONS de totes les llibreries de Spring -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>

    <!-- 2. DADES DEL NOSTRE PROJECTE -->
    <groupId>cat.iesabadell</groupId>
    <artifactId>videojocsSpring</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>videojocsSpring</name>

    <!-- 3. PROPIETATS -->
    <properties>
        <java.version>21</java.version>
    </properties>

    <!-- 4. DEPENDÈNCIES: SENSE <version>, la posa el pare -->
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Swagger UI. No és de Spring: el parent no en fixa la versió i cal posar-la -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>3.1.1</version>
        </dependency>

        <!-- Tests: a Spring Boot 4 hi ha un starter de test per tecnologia -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <!-- 5. PLUGINS: empaqueta un .jar executable (java -jar videojocsSpring.jar) -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

| Part | Què fa |
|---|---|
| `<parent>` | És la peça clau. Fixa les versions de **totes** les llibreries de l'ecosistema (Hibernate, Jackson, Tomcat, driver de MySQL...) perquè funcionin juntes. Per això les dependències no porten `<version>` |
| `<version>` a springdoc | És l'única excepció: no és una llibreria de Spring i el *parent* no la coneix. La branca 3.x és la de Spring Boot 4 |
| `scope runtime` | El driver de MySQL no el fem servir mai des del codi (no hi ha cap `import com.mysql...`), només en executar |
| `scope test` | Només per als tests; no va al `.jar` final |
| Comparat amb Hibernate | Allà posàvem `hibernate-core`, `hibernate-validator`, `expressly`, `mysql-connector-j`... cadascun amb la seva versió. Aquí tot això ve dins de `data-jpa` i `validation` |

Les llibreries que ha baixat Maven es veuen a Eclipse a `Maven Dependencies`: en són moltes, i totes les ha triat el *parent*.

> Si canvieu el `pom.xml` i Eclipse no ho detecta: clic dret al projecte → `Maven > Update Project...` → marcar *Force Update of Snapshots/Releases*.

---

<a id="estructura"></a>
## 5. Estructura de paquets

```
videojocsSpring/
├── pom.xml
├── mvnw, mvnw.cmd                       <- Maven wrapper: compila sense tenir Maven instal·lat
└── src/
    ├── main/
    │   ├── java/cat/iesabadell/videojocs/
    │   │   ├── VideojocsSpringApplication.java    <- classe principal (paquet ARREL)
    │   │   ├── model/          entitats (@Entity) i enums
    │   │   ├── repository/     interfícies de Spring Data (@Repository)
    │   │   ├── service/        lògica de negoci i transaccions (@Service)
    │   │   ├── controller/     endpoints REST (@RestController)
    │   │   ├── dto/            objectes d'entrada i sortida de l'API (sessió 2)
    │   │   ├── exception/      gestor d'errors de validació (sessió 2)
    │   │   └── config/         càrrega inicial dels CSV (sessió 3)
    │   └── resources/
    │       ├── application.properties
    │       ├── dades/          CSV de càrrega inicial (sessió 3)
    │       ├── static/         fitxers web estàtics (annex Thymeleaf)
    │       └── templates/      plantilles Thymeleaf (annex)
    └── test/java/cat/iesabadell/videojocs/
        └── VideojocsSpringApplicationTests.java
```

**La regla més important:** Spring només troba les classes que són **al paquet de la classe principal o en subpaquets seus**. Si poseu un controlador a `cat.iesabadell.controller` (germà, no fill, de `cat.iesabadell.videojocs`), Spring no el veurà: no donarà cap error, però l'URL retornarà 404.

```
cat.iesabadell.videojocs              <- classe principal
cat.iesabadell.videojocs.controller   <- OK (subpaquet)
cat.iesabadell.controller             <- NO el troba
```

Els noms dels paquets (`model`, `repository`...) són convenció, no obligació. També és vàlid agrupar per funcionalitat (`videojoc/`, `estudi/`...); el que no és opcional és que estiguin a sota del paquet arrel.

---

<a id="main"></a>
## 6. La classe principal

```java
package cat.iesabadell.videojocs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VideojocsSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideojocsSpringApplication.class, args);
    }
}
```

`@SpringBootApplication` en són tres en una:

| Anotació | Què fa |
|---|---|
| `@SpringBootConfiguration` | La classe pot declarar beans (`@Bean`) |
| `@EnableAutoConfiguration` | Activa l'autoconfiguració ([§1](#que-es)) |
| `@ComponentScan` | Busca classes anotades (`@Service`, `@RestController`...) **en aquest paquet i subpaquets** ([§5](#estructura)) |

`SpringApplication.run(...)` arrenca el contenidor, crea tots els beans, connecta a la BD, crea les taules si cal i engega el Tomcat. El `main` no acaba: l'aplicació queda escoltant fins que l'aturem.

**Desactivar una autoconfiguració concreta** (poques vegades cal):

```java
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
```

**`@EntityScan` i `@EnableJpaRepositories`**: indiquen on són les entitats i els repositoris quan **no** són a sota del paquet principal (per exemple, si reutilitzem un `.jar` d'entitats d'un altre projecte). Si seguim l'estructura de [§5](#estructura), **no cal posar-les**.

```java
// Només si les entitats són FORA del paquet arrel
@SpringBootApplication
@EntityScan("cat.iesabadell.comu.model")              // Spring Boot 4: org.springframework.boot.persistence.autoconfigure.EntityScan
@EnableJpaRepositories("cat.iesabadell.comu.repository")
public class VideojocsSpringApplication { ... }
```

---

<a id="properties"></a>
## 7. `application.properties`

Fitxer `src/main/resources/application.properties`. Substitueix el `hibernate.cfg.xml`.

<a id="prop-connexio"></a>
### 7.1 Connexió a MySQL

```properties
spring.application.name=videojocsSpring

# --- Connexió ---
spring.datasource.url=jdbc:mysql://localhost:3306/videojocs_spring?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false
spring.datasource.username=root
spring.datasource.password=super3
```

| Paràmetre de l'URL | Per a què |
|---|---|
| `createDatabaseIfNotExist=true` | Crea la BD si no existeix (no cal fer el `CREATE DATABASE`) |
| `allowPublicKeyRetrieval=true` | Necessari amb usuaris de MySQL 8+ si la connexió no és SSL |
| `useSSL=false` | En local no fem servir SSL |

No cal posar el driver (`spring.datasource.driver-class-name`) ni el dialecte (`spring.jpa.database-platform`): Spring Boot i Hibernate els dedueixen de l'URL. Si poseu el dialecte, Hibernate avisa al log que no cal.

<a id="prop-ddl"></a>
### 7.2 `ddl-auto`

```properties
spring.jpa.hibernate.ddl-auto=update
```

És el mateix que `hbm2ddl.auto` d'Hibernate:

| Valor | Què fa en arrencar | Quan |
|---|---|---|
| `none` | Res. **És el valor per defecte amb MySQL**: si no el poseu, no es creen les taules | Producció |
| `validate` | Comprova que les taules coincideixen amb les entitats; si no, l'aplicació no arrenca | Producció amb BD creada a part |
| `update` | Crea el que falta, no esborra res | Mentre desenvolupem amb dades que volem conservar |
| `create` | Esborra les taules i les torna a crear | Mentre desenvolupem amb dades que es carreguen en arrencar (sessió 3) |
| `create-drop` | Com `create`, i les esborra en aturar | Tests |

> `update` no ho arregla tot: si canvieu el tipus d'una columna o en traieu una, no la toca. Si l'esquema queda brut, esborreu la BD i torneu a arrencar.

<a id="prop-sql"></a>
### 7.3 Veure l'SQL

```properties
# Opció ràpida: imprimeix l'SQL per consola (sense paràmetres)
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Opció recomanada: pel sistema de log, amb els valors dels paràmetres (?)
#logging.level.org.hibernate.SQL=debug
#logging.level.org.hibernate.orm.jdbc.bind=trace
```

Tingueu-lo activat mentre aprenem: és la manera de veure quantes `SELECT` fa cada operació (sessió 3, LAZY i EAGER).

<a id="prop-altres"></a>
### 7.4 Altres propietats útils

```properties
# Port del servidor (per defecte 8080)
server.port=8081

# Desactiva "Open Session in View" (sessió 3 ho explica). Recomanat
spring.jpa.open-in-view=false

# Missatges d'error més detallats a la resposta HTTP (només per desenvolupar)
server.error.include-message=always
```

Qualsevol propietat d'Hibernate es pot passar amb el prefix `spring.jpa.properties.`:

```properties
spring.jpa.properties.hibernate.jdbc.batch_size=20
```

<a id="prop-cfg"></a>
### 7.5 De `hibernate.cfg.xml` a `application.properties`

| `hibernate.cfg.xml` | `application.properties` |
|---|---|
| `hibernate.connection.url` | `spring.datasource.url` |
| `hibernate.connection.username` / `password` | `spring.datasource.username` / `password` |
| `hibernate.dialect` | No cal |
| `hibernate.hbm2ddl.auto` | `spring.jpa.hibernate.ddl-auto` |
| `hibernate.show_sql` / `format_sql` | `spring.jpa.show-sql` / `spring.jpa.properties.hibernate.format_sql` |
| `<mapping class="relacions.Videojoc"/>` | No cal: Spring troba totes les `@Entity` del paquet arrel |
| `current_session_context_class` | No cal: les transaccions les gestiona Spring |

<a id="prop-noms"></a>
### 7.6 Noms de taules i columnes: compte!

Spring Boot canvia l'estratègia de noms d'Hibernate: els noms en *camelCase* passen a *snake_case* **encara que els escriviu a `@Column(name=...)` o `@Table(name=...)`**.

| A l'entitat | Hibernate sol | Spring Boot |
|---|---|---|
| `private int horesSetmana;` | `horesSetmana` | `hores_setmana` |
| `@Column(name = "IdModul")` | `IdModul` | `id_modul` |
| `@Entity class FitxaTecnica` | `FitxaTecnica` | `fitxa_tecnica` |

Si les entitats ja fan servir *snake_case* (com les de la sessió de relacions: `estudi_id`, `fitxa_tecnica`...), no notareu cap diferència. Si treballeu sobre una BD que ja existeix amb noms en *camelCase*, i voleu que es respectin tal com els escriviu:

```properties
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
```

---

<a id="di"></a>
## 8. Beans i injecció de dependències

Un **bean** és un objecte que crea i gestiona Spring. Una classe es converteix en bean quan porta una d'aquestes anotacions (i és a sota del paquet arrel):

| Anotació | Capa | Què hi afegeix Spring |
|---|---|---|
| `@Component` | Genèrica | Res especial |
| `@Service` | Servei (lògica de negoci) | Res especial: indica el rol |
| `@Repository` | Accés a dades | Tradueix les excepcions de la BD a excepcions de Spring. Als repositoris de Spring Data no cal posar-la |
| `@Controller` | Web amb vistes (annex) | Els mètodes retornen el nom d'una plantilla |
| `@RestController` | Web REST | Els mètodes retornen dades (JSON) |
| `@Configuration` + `@Bean` | Configuració | Per crear beans de classes que no són nostres |

**Injecció de dependències**: si un bean necessita un altre bean, el demana i Spring li dona. No fem mai `new` d'un servei o d'un repositori.

### Per atribut amb `@Autowired` (el que fem a classe)

```java
@Service
public class PlataformaService {

	// Spring ens injecta el repositori (no fem mai new)
	@Autowired
	private PlataformaRepository repository;
}
```

En arrencar, Spring crea un `PlataformaRepository` i l'assigna a l'atribut. No calen getters ni setters de l'atribut.

### Per constructor (l'alternativa)

```java
@Service
public class PlataformaService {

	private final PlataformaRepository repository;

	// Un sol constructor: Spring el fa servir sol, no cal @Autowired
	public PlataformaService(PlataformaRepository repository) {
		this.repository = repository;
	}
}
```

És la que recomana la documentació de Spring i la que trobareu en molts projectes. Fa exactament el mateix:

| | Per atribut (`@Autowired`) | Per constructor |
|---|---|---|
| Codi | Més curt: una anotació per dependència | Un constructor amb totes les dependències |
| L'atribut pot ser `final` | No | Sí: no es pot canviar per error |
| Es veu de què depèn la classe | Cal buscar els `@Autowired` | Al constructor |
| Fer un test sense Spring | Cal Spring (o reflexió) per omplir l'atribut | `new PlataformaService(repoFals)` |

Si en un projecte trobeu l'una o l'altra, funcionen igual.

### Els beans són únics (*singleton*)

Spring crea **una sola instància** de cada servei i de cada controlador, i la comparteixen **totes les peticions** alhora. Conseqüència:

```java
@Service
public class PartidaService {
	private Partida partidaActual;      // MALAMENT: el comparteixen tots els jugadors i totes les peticions
}
```

Els serveis i controladors **no guarden estat** entre peticions: només hi ha atributs per als beans injectats. L'estat va a la BD (la partida activa es busca al repositori cada vegada).

---

<a id="capes"></a>
## 9. L'arquitectura en capes

<a id="mvc"></a>
### 9.1 MVC i Front Controller

**MVC (Model-Vista-Controlador)** separa una aplicació en tres parts:

```
          petició                 ┌──────────────┐
 Client ─────────────────────────►│ Controlador  │  rep la petició i decideix què fer
   ▲                              └──────┬───────┘
   │                                     │ demana / modifica
   │                              ┌──────▼───────┐
   │                              │    Model     │  dades i lògica (entitats, serveis, BD)
   │                              └──────┬───────┘
   │      resposta                ┌──────▼───────┐
   └──────────────────────────────│    Vista     │  presenta el resultat (HTML o JSON)
                                  └──────────────┘
```

En una **API REST** la vista és el **JSON** que genera Jackson automàticament. En una aplicació amb plantilles (annex Thymeleaf), la vista és una pàgina HTML.

**Front Controller**: totes les peticions entren per **un sol punt**, que les reparteix. A Spring és el **`DispatcherServlet`**, que crea Spring Boot sol:

```
                       ┌───────────────────────┐
 GET /api/plataformes ►│                       │──► PlataformaController.llistar()
 POST /api/estudis ───►│   DispatcherServlet   │──► EstudiController.crear(...)
 GET /api/videojocs/3 ►│   (Front Controller)  │──► VideojocController.buscar(3)
                       └───────────────────────┘
          Mira el mètode HTTP i l'URL i busca el @GetMapping / @PostMapping que hi encaixa
```

Nosaltres no veiem mai el `DispatcherServlet`: només escrivim els controladors.

<a id="csr"></a>
### 9.2 Controlador, servei i repositori

```
 HTTP ──► @RestController ──► @Service ──► Repository (Spring Data) ──► Hibernate ──► JDBC ──► MySQL
          Controlador         Servei       Repositori
```

| Capa | Responsabilitat | Exemple |
|---|---|---|
| **Controlador** | Rebre la petició, llegir els paràmetres (`@PathVariable`, `@RequestParam`, `@RequestBody`) i passar-los al servei | `PlataformaController` |
| **Servei** | Lògica de negoci: regles, comprovacions, operacions que toquen diverses entitats, **transaccions**. Construeix la resposta: `ResponseEntity` amb el codi d'estat, la capçalera i el cos | `PlataformaService` |
| **Repositori** | Accés a dades: CRUD i consultes | `PlataformaRepository` |

El controlador queda molt curt: cada mètode és una línia que crida el servei. Tota la feina és al servei.

Què **no** va a cada capa:

| No feu això | Per què |
|---|---|
| `@GetMapping`, `@RequestParam` o `@PathVariable` en un servei | Les anotacions web només tenen efecte al controlador. Al servei no fan res i confonen |
| Un controlador que crida directament el repositori | Les regles de negoci acaben escampades pels controladors i no hi ha lloc per a les transaccions |
| Lògica de negoci (comprovar recursos, torns...) al controlador | No es pot reutilitzar des d'un altre controlador ni des d'un test |
| `findById(id).get()` sense comprovar si hi és | Si no existeix, `NoSuchElementException` i un `500`. Cal `isPresent()` i respondre `404` |

<a id="dao-spring"></a>
### 9.3 Del DAO fort d'Hibernate a Spring

Tot el que vam muntar a la sessió del patró DAO té un equivalent a Spring, i la majoria **ja ve fet**:

| Hibernate (sessió DAO) | Spring | Qui ho escriu |
|---|---|---|
| `SessionManager` (singleton de la `SessionFactory`) | `EntityManagerFactory` autoconfigurada | Spring Boot |
| `IGenericDao<T, ID>` + `GenericDao<T, ID>` | `JpaRepository<T, ID>` | Spring Data (nosaltres només la interfície buida) |
| `findOneBy("nom", ...)`, consultes HQL | Mètodes derivats (`findByNom`) i `@Query` | Nosaltres (només la signatura) |
| `VideojocDao` **fort**: les operacions de l'aplicació | `VideojocService` | Nosaltres |
| Mètode públic: obrir transacció, commit, rollback | `@Transactional` al mètode del servei | Spring |
| Mètodes privats que reben la `Session` | Mètodes normals: la transacció es propaga sola | Nosaltres |
| `Main` que crida el DAO | Controlador que crida el servei | Nosaltres |

La idea de **"un DAO fort per agregat"** es manté: a Spring els **repositoris** són prims (un per entitat, i gairebé buits) i el **servei** és el fort. Un `VideojocService` pot fer servir els repositoris de videojocs, categories i plataformes per fer una operació completa dins d'una sola transacció. Ho fem a la sessió 3.

---

<a id="exemple"></a>
## 10. Primer exemple complet: `Plataforma`

El codi d'aquesta secció és el del projecte d'exemple `videojocsSpring`.

**Primer pas:** copieu les sis entitats de la sessió de relacions d'Hibernate (`Estudi`, `Videojoc`, `FitxaTecnica`, `Categoria`, `Plataforma`, `Llancament`) al paquet `model` i canvieu-ne la línia `package`. Han d'anar juntes perquè es refereixen les unes a les altres. En arrencar, Spring crearà les sis taules.

Avui només fem endpoints de `Plataforma`, l'entitat més senzilla: no té relacions pròpies (és `Llancament` qui apunta a `Plataforma`), i per tant el JSON no ens donarà problemes. Les relacions arriben a la sessió 3.

<a id="ex-entitat"></a>
### 10.1 L'entitat

La mateixa de la sessió de relacions:

```java
package cat.iesabadell.videojocs.model;

import jakarta.persistence.*;

@Entity
@Table(name="plataforma")
public class Plataforma {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="id")
	private Integer id;

	@Column(name="nom", nullable=false, unique=true, length=30)
	private String nom;

	// JPA necessita el constructor buit
	public Plataforma() {
	}

	public Plataforma(String nom) {
		this.nom = nom;
	}

	public Integer getId() {
		return id;
	}

	public String getNom() {
		return nom;
	}

	public void setNom(String nom) {
		this.nom = nom;
	}

	// equals/hashCode: patró de la sessió 1 d'Hibernate (id Integer, que és null fins que es desa)
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Plataforma other))
			return false;
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Plataforma.class.hashCode();
	}

	@Override
	public String toString() {
		return "Plataforma [id=" + id + ", nom=" + nom + "]";
	}

}
```

Res de nou: `jakarta.persistence`, les mateixes anotacions, les mateixes validacions. Spring Data JPA fa servir Hibernate per sota.

> **Getters obligatoris per al JSON.** Jackson converteix l'objecte en JSON a partir dels **getters** (`getId()` → `"id"`, `getNom()` → `"nom"`). Un atribut sense getter no surt al JSON.

<a id="ex-repositori"></a>
### 10.2 El repositori

```java
package cat.iesabadell.videojocs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cat.iesabadell.videojocs.model.Plataforma;

// JpaRepository<Entitat, tipus de l'@Id>: tot el CRUD sense escriure cap implementació
public interface PlataformaRepository extends JpaRepository<Plataforma, Integer> {

	// Consultes derivades del nom del mètode (sessió 4). Spring escriu la implementació
	Optional<Plataforma> findByNom(String nom);

	boolean existsByNom(String nom);

}
```

**Això és tot.** No hi ha cap classe que implementi la interfície: Spring en genera una en arrencar. És el `GenericDao` de la sessió DAO, però sense escriure'l.

Jerarquia de repositoris de Spring Data:

```
Repository<T, ID>                              (marcador, sense mètodes)
  └── CrudRepository<T, ID>                    save, findById, findAll, count, delete... (retorna Iterable)
        └── ListCrudRepository<T, ID>          els mateixos, retornant List
PagingAndSortingRepository<T, ID>              findAll(Sort), findAll(Pageable)   (ja NO hereta de CrudRepository)
  └── ListPagingAndSortingRepository<T, ID>
JpaRepository<T, ID>  =  ListCrudRepository + ListPagingAndSortingRepository + QueryByExampleExecutor
                         + flush, saveAndFlush, deleteAllInBatch, getReferenceById
```

Farem servir **`JpaRepository`**: ho té tot i retorna `List` (amb `CrudRepository`, `findAll()` retorna `Iterable` i no es pot fer `.size()` ni recórrer amb índex).

Mètodes que hereta:

| Mètode | Retorna | Equivalent Hibernate |
|---|---|---|
| `save(entitat)` | L'entitat desada (**feu servir aquesta**, no la que heu passat) | `persist` si és nova, `merge` si no |
| `saveAll(llista)` | `List<T>` | |
| `findById(id)` | `Optional<T>` | `session.find` (que retornava `null`) |
| `findAll()` | `List<T>` | `SELECT e FROM Entitat e` |
| `findAll(Sort.by("nom"))` | `List<T>` ordenada | `ORDER BY` |
| `findAllById(ids)` | `List<T>` | |
| `existsById(id)` | `boolean` | |
| `count()` | `long` | |
| `deleteById(id)` / `delete(entitat)` | `void` | `remove` |
| `deleteAll()` | `void` | Esborra **una per una** (amb cascades) |
| `deleteAllInBatch()` | `void` | Un sol `DELETE` (sense cascades de JPA) |
| `getReferenceById(id)` | Proxy sense consultar la BD | `session.getReference` |
| `flush()` / `saveAndFlush(e)` | | `session.flush()` |

> **`save` d'una entitat nova o existent?** Si l'`@Id` és `null` (o 0 en un `int`), fa `persist`. Si no, fa `merge`. Aquest és un motiu més per fer servir `Integer` a l'`@Id` i no `int`.

**`Optional<T>`**: una capsa que pot contenir l'objecte o estar buida. Substitueix el `null` de `session.find`:

```java
Optional<Plataforma> p = repository.findById(id);
if (p.isPresent()) {
	Plataforma plataforma = p.get();    // només si isPresent()
}
```

#### Consultes derivades del nom del mètode

`findByNom` i `existsByNom` no els implementa ningú: Spring **llegeix el nom del mètode** i n'escriu la consulta. Es poden combinar paraules clau per fer cerques sense escriure SQL:

```java
	// Plataformes que COMENCEN per un text, sense distingir majúscules, ordenades per nom
	//   SELECT ... WHERE UPPER(nom) LIKE UPPER('text%') ORDER BY nom
	List<Plataforma> findByNomStartingWithIgnoreCaseOrderByNom(String inici);

	// Plataformes que CONTENEN un text
	//   SELECT ... WHERE UPPER(nom) LIKE UPPER('%text%') ORDER BY nom
	List<Plataforma> findByNomContainingIgnoreCaseOrderByNom(String text);

	// Quantes plataformes comencen per un text: no carrega les entitats, fa un SELECT COUNT
	long countByNomStartingWithIgnoreCase(String inici);
```

Com es llegeix el nom:

```
find   By   Nom        StartingWith   IgnoreCase    OrderBy   Nom
└┬─┘   └┬┘  └─┬─┘      └────┬─────┘   └────┬────┘   └──┬──┘   └┬┘
què    on   atribut    condició        sense         ordre    per quin
vols        de l'entitat              majúscules              atribut
```

| Part | Opcions | Exemple |
|---|---|---|
| Què | `find...By` (entitats), `count...By` (un número), `exists...By` (`true`/`false`) | `countByNom...` |
| Atribut | Qualsevol atribut de l'entitat, amb majúscula inicial | `Nom` |
| Condició | `StartingWith`, `EndingWith`, `Containing`, `LessThan`, `Between`, `IsNull`... (sessió 4) | `StartingWith` |
| Modificadors | `IgnoreCase`, `OrderBy...Asc/Desc`, `And`, `Or` | `IgnoreCase`, `OrderByNom` |

- El nom ha de coincidir amb l'**atribut Java** (`nom`), no amb la columna de la BD.
- Si el nom no té sentit (`findByNmo`), l'aplicació **no arrenca**: l'error surt en arrencar, no quan es crida.
- **Autocompletar**: amb Spring Tools a Eclipse (o IntelliJ), dins del repositori escriviu `findBy` i premeu Ctrl+Espai: proposa els atributs i les condicions.
- Activeu `show-sql` i mireu l'SQL que genera cada mètode.

Al servei i al controlador, com la resta:

```java
	// Servei
	public ResponseEntity<List<Plataforma>> comencenPer(String inici) {
		List<Plataforma> plataformes = repository.findByNomStartingWithIgnoreCaseOrderByNom(inici);
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Plataformes trobades: " + plataformes.size())
				.body(plataformes);
	}

	// Sense paràmetre: count() heretat de JpaRepository. Amb paràmetre: la consulta derivada countBy...
	public ResponseEntity<Long> total(String inici) {
		long total;
		if (inici == null) {
			total = repository.count();
		} else {
			total = repository.countByNomStartingWithIgnoreCase(inici);
		}
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Total de plataformes")
				.body(total);
	}
```

```java
	// Controlador
	// GET localhost:8080/api/plataformes/comencen?inici=p
	@GetMapping(path="/comencen")
	public ResponseEntity<List<Plataforma>> comencenPer(@RequestParam String inici) {
		return service.comencenPer(inici);
	}

	// GET localhost:8080/api/plataformes/total          -> totes
	// GET localhost:8080/api/plataformes/total?inici=p  -> les que comencen per p
	@GetMapping(path="/total")
	public ResponseEntity<Long> total(@RequestParam(required = false) String inici) {
		return service.total(inici);
	}
```

`contenen` (`GET /api/plataformes/contenen?text=s`) és igual que `comencenPer`. `required = false`: si el paràmetre no hi és, arriba `null`.

> `/comencen`, `/contenen` i `/total` no xoquen amb `/{id}`: Spring dona prioritat a les rutes fixes. I com que són `GET`, es poden provar des del navegador.

<a id="ex-servei"></a>
### 10.3 El servei

El servei fa la feina **i construeix la resposta**: un `ResponseEntity` amb el codi d'estat HTTP, una capçalera pròpia (`Header-Customitzat`) amb informació per a qui consumeix l'API, i el cos.

```java
package cat.iesabadell.videojocs.service;

@Service
public class PlataformaService {

	// Spring ens injecta el repositori (no fem mai new)
	@Autowired
	private PlataformaRepository repository;

	public ResponseEntity<List<Plataforma>> llistar() {
		List<Plataforma> plataformes = repository.findAll(Sort.by("nom"));
		// Una llista buida és un resultat correcte: 200 + []
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Total plataformes: " + plataformes.size())
				.body(plataformes);
	}

	// ResponseEntity<?>: el cos pot ser una Plataforma (si la trobem) o un String (si no)
	public ResponseEntity<?> buscar(int id) {
		Optional<Plataforma> p = repository.findById(id);
		if (p.isPresent()) {
			return ResponseEntity.ok()
					.header("Header-Customitzat", "Dades plataforma ID: " + id)
					.body(p.get());
		} else {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.header("Header-Customitzat", "ID " + id + " NO TROBAT")
					.body("NO EXISTEIX CAP PLATAFORMA AMB ID " + id);
		}
	}

	public ResponseEntity<String> crear(String nom) {
		// Regla de negoci: no hi pot haver dues plataformes amb el mateix nom
		if (repository.existsByNom(nom)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.header("Header-Customitzat", "PLATAFORMA DUPLICADA")
					.body("ERROR: JA EXISTEIX LA PLATAFORMA " + nom);
		}
		Plataforma p = repository.save(new Plataforma(nom));
		return ResponseEntity.status(HttpStatus.CREATED)
				.header("Header-Customitzat", "Plataforma creada amb ID: " + p.getId())
				.body("PLATAFORMA INSERIDA: " + p);
	}

}
```

| Peça | Què és |
|---|---|
| `ResponseEntity<T>` | La resposta HTTP sencera: codi + capçaleres + cos de tipus `T` (sessió 2) |
| `ResponseEntity<?>` | El cos pot ser de qualsevol tipus. Aquí: una `Plataforma` si la troba, un `String` amb l'error si no |
| `ResponseEntity.ok()` | Codi `200` |
| `ResponseEntity.status(HttpStatus.NOT_FOUND)` | Qualsevol codi |
| `.header("Header-Customitzat", "...")` | Una capçalera pròpia. **Sense accents**: les capçaleres HTTP no estan pensades per a caràcters fora de l'ASCII i poden arribar malmeses. El missatge amb accents, al cos |
| `.body(...)` | El cos. Un objecte es converteix en JSON; un `String` s'envia com a text |

<a id="ex-controlador"></a>
### 10.4 El controlador

```java
package cat.iesabadell.videojocs.controller;

// Fa que la classe sigui un controlador REST: els mètodes retornen DADES (JSON), no vistes
@RestController
// Prefix de totes les URL d'aquesta classe: localhost:8080/api/plataformes
@RequestMapping(path="/api/plataformes")
public class PlataformaController {

	// Connectem automàticament el servei amb @Autowired
	@Autowired
	private PlataformaService service;

	// GET localhost:8080/api/plataformes
	@GetMapping
	public ResponseEntity<List<Plataforma>> llistar() {
		return service.llistar();
	}

	// GET localhost:8080/api/plataformes/2
	@GetMapping(path="/{id}")
	public ResponseEntity<?> buscar(@PathVariable int id) {
		return service.buscar(id);
	}

	// POST localhost:8080/api/plataformes?nom=Xbox
	@PostMapping
	public ResponseEntity<String> crear(@RequestParam String nom) {
		return service.crear(nom);
	}

}
```

| Anotació | Què fa |
|---|---|
| `@RestController` | `@Controller` + `@ResponseBody`: el que retorna el mètode és el **cos** de la resposta (convertit a JSON), no el nom d'una plantilla |
| `@RequestMapping(path="/api/plataformes")` | Prefix comú. El `/api` separa l'API d'altres URL (pàgines, Swagger...) |
| `@GetMapping`, `@PostMapping`... | El mètode atén aquest verb HTTP en aquesta URL (sessió 2) |
| `@PathVariable` | Valor que forma part de l'URL: `/api/plataformes/{id}` |
| `@RequestParam` | Paràmetre de consulta: `?nom=Xbox` |
| `@Autowired` | Spring hi posa el servei ([§8](#di)) |

> En aquest primer exemple el `POST` rep el nom per `@RequestParam` perquè es pugui provar fàcilment. A la sessió 2, amb `Estudi`, les dades arriben com a JSON al cos (`@RequestBody`), que és com es fa en una API.

<a id="ex-executar"></a>
### 10.5 Executar i provar

**Executar**: clic dret a `VideojocsSpringApplication` → `Run As > Spring Boot App` (o `Java Application`, que també funciona). Des de terminal: `./mvnw spring-boot:run`.

Al log, cal veure (en aquest ordre):

```
Starting VideojocsSpringApplication using Java 21...
HikariPool-1 - Start completed.                       <- connexió a MySQL correcta
Hibernate: create table plataforma (...)              <- ddl-auto
Tomcat started on port 8080 (http)                    <- servidor escoltant
Started VideojocsSpringApplication in 3.1 seconds
```

**Provar**:

| Petició | Com | Resultat esperat |
|---|---|---|
| `GET /api/plataformes` | Navegador: `http://localhost:8080/api/plataformes` | `200` · `[]` (encara no n'hi ha; amb el projecte d'exemple, les tres dels CSV) |
| `POST /api/plataformes?nom=PC` | Postman o `curl` | `201` · `PLATAFORMA INSERIDA: Plataforma [id=1, nom=PC]` |
| `POST /api/plataformes?nom=PC` (un altre cop) | Postman o `curl` | `409` · `ERROR: JA EXISTEIX LA PLATAFORMA PC` |
| `GET /api/plataformes/1` | Navegador | `200` · `{"id":1,"nom":"PC"}` |
| `GET /api/plataformes/99` | Navegador | `404` · `NO EXISTEIX CAP PLATAFORMA AMB ID 99` |
| `GET /api/plataformes/comencen?inici=p` | Navegador | `200` · les que comencen per `p` o `P` |
| `GET /api/plataformes/contenen?text=s` | Navegador | `200` · les que contenen una `s` |
| `GET /api/plataformes/total?inici=p` | Navegador | `200` · un número |
| `POST /api/plataformes?nom=Xbox` des del **navegador** | Barra d'adreces | `200` · la **llista**: el navegador fa `GET` i s'executa `llistar()`. No es crea res (sessió 2) |

Escrivint l'URL al navegador només es poden fer peticions `GET`. Per a la resta hi ha tres opcions:

**1. Swagger UI** (el més ràpid a classe): obriu `http://localhost:8080/swagger-ui.html`. Surten tots els endpoints agrupats per controlador. Per provar-ne un: desplegar-lo → *Try it out* → omplir els paràmetres → *Execute*. A sota es veu la resposta: el codi, el cos i les capçaleres (també el `Header-Customitzat`).

Swagger UI llegeix els controladors en arrencar i genera la pàgina sola: no cal escriure res. Cada endpoint nou que afegiu hi apareix automàticament. La descripció en format OpenAPI (JSON) és a `http://localhost:8080/v3/api-docs`.

**2. [Postman](https://www.postman.com/downloads/)**: per guardar les peticions en una col·lecció i tornar-les a passar. El projecte d'exemple en porta una amb totes les peticions de les quatre sessions.

**3. `curl`** des del terminal:

```bash
curl -i -X POST "http://localhost:8080/api/plataformes?nom=PC"
```

`-i` mostra també el codi i les capçaleres.

**Aturar**: el quadrat vermell de la consola d'Eclipse. Si no l'atureu i torneu a executar, el segon arrencament fallarà perquè el port ja està ocupat ([§13](#errors)).

---

<a id="recorregut"></a>
## 11. El recorregut d'una petició

Què passa quan arriba `GET /api/plataformes/1`:

```
1. Tomcat rep la petició HTTP i la passa al DispatcherServlet
2. El DispatcherServlet busca el mètode: GET + /api/plataformes/{id}  ->  PlataformaController.buscar
3. Converteix "1" de l'URL a int (@PathVariable int id)
4. Crida controller.buscar(1)
       └── service.buscar(1)
               └── repository.findById(1)    -> Hibernate -> SELECT ... FROM plataforma WHERE id=?
           el servei construeix ResponseEntity: 200 + Header-Customitzat + la plataforma
5. El controlador retorna el ResponseEntity tal qual
6. Jackson converteix la Plataforma a JSON fent servir els getters:  {"id":1,"nom":"PC"}
7. Tomcat envia la resposta: 200 OK, Header-Customitzat: Dades plataforma ID: 1, Content-Type: application/json
```

Cap línia d'aquest recorregut obre una `Session` ni una transacció a mà: el repositori ho fa sol per a cada crida. Quan una operació necessita **diverses crides en una sola transacció**, cal `@Transactional` al servei (sessió 3).

---

<a id="catan"></a>
## 12. Cap a Catan

| A la part Spring de Catan | Avui |
|---|---|
| Crear el projecte Maven amb Spring Boot | [§3](#crear), [§4](#pom). Spring Data JDBC i Rest Repositories no són necessaris |
| Les entitats d'Hibernate es reutilitzen | [§10.1](#ex-entitat): es copien al paquet `model` sense canvis |
| Repositoris: un per entitat que calgui consultar, gairebé buits | [§10.2](#ex-repositori) |
| La lògica del DAO fort (`PartidaDao`, `JugadorDao`) passa als serveis | [§9.3](#dao-spring) |
| Controladors `/partida/...` i `/jugador/...` | [§10.4](#ex-controlador) |
| Els serveis no guarden la partida en un atribut | [§8](#di) (beans *singleton*) |

---

<a id="errors"></a>
## 13. Errors típics

| Error | Causa | Solució |
|---|---|---|
| `Web server failed to start. Port 8080 was already in use` | L'aplicació ja s'està executant (una altra pestanya de consola) | Aturar-la, o canviar `server.port` ([§7.4](#prop-altres)) |
| `Communications link failure` | MySQL no està engegat o l'URL és incorrecta | Engegar MySQL; revisar `spring.datasource.url` |
| `Access denied for user 'root'@'localhost'` | Usuari o contrasenya | `spring.datasource.username` / `password` |
| `Public Key Retrieval is not allowed` | Falta el paràmetre a l'URL | `allowPublicKeyRetrieval=true` ([§7.1](#prop-connexio)) |
| L'aplicació arrenca però no es crea cap taula | `ddl-auto` és `none` per defecte | `spring.jpa.hibernate.ddl-auto=update` ([§7.2](#prop-ddl)) |
| `404 Not Found` (Whitelabel Error Page) en una URL que hauria d'existir | El controlador és fora del paquet arrel, o l'URL no coincideix (`/api/plataforma` vs `/api/plataformes`) | [§5](#estructura); revisar `@RequestMapping` |
| `405 Method Not Allowed` | Heu fet un `GET` (navegador) a una URL que només té `@PostMapping` | Fer la petició amb Swagger UI, Postman o `curl` |
| `swagger-ui.html` dona `404` | Falta la dependència de springdoc, o no s'ha recarregat Maven | [§4](#pom) i `Maven > Update Project...` |
| `400 Bad Request: Required parameter 'nom' is not present` | Falta un `@RequestParam` obligatori | Afegir-lo a l'URL, o `@RequestParam(required = false)` |
| `No qualifying bean of type 'PlataformaRepository'` | El repositori és fora del paquet arrel, o no hereta de `JpaRepository` | [§5](#estructura), [§10.2](#ex-repositori) |
| `Not a managed type: class ...Plataforma` | Falta `@Entity`, o l'entitat és fora del paquet arrel | Afegir `@Entity` / moure-la, o `@EntityScan` ([§6](#main)) |
| `NullPointerException` en cridar `service.xxx()` des del controlador | Falta `@Autowired` a l'atribut, o s'ha creat el servei amb `new` | `@Autowired` ([§8](#di)) |
| `NoSuchElementException: No value present` | `findById(id).get()` d'un id que no existeix | `isPresent()` abans de `get()` i respondre `404` ([§10.3](#ex-servei)) |
| Columnes amb noms diferents dels de l'entitat | Estratègia de noms de Spring Boot | [§7.6](#prop-noms) |
| Classes en vermell després de canviar el `pom.xml` | Eclipse no ha recarregat Maven | `Maven > Update Project...` ([§4](#pom)) |
