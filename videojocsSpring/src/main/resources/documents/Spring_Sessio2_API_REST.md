# Spring Boot · Sessió 2: l'API REST

**M0486 Accés a dades · M0490 BA2 Desenvolupament i consum d'API · 2n DAMVi**

---

## Índex

1. [Què és una API REST](#rest)
2. [Dissenyar les URL](#url)
3. [Els mètodes HTTP](#metodes)
4. [Els codis d'estat](#codis)
5. [Rebre dades](#rebre)
   - [5.1 `@PathVariable`](#pathvariable)
   - [5.2 `@RequestParam`](#requestparam)
   - [5.3 `@RequestBody`](#requestbody)
   - [5.4 Quan fer servir cadascun](#quan)
6. [Retornar dades: `ResponseEntity`](#retornar)
7. [DTO: no exposar les entitats](#dto)
8. [Validar l'entrada](#validar)
9. [Gestió d'errors](#errors-api)
   - [9.1 El servei decideix el codi](#llancar)
   - [9.2 `@RestControllerAdvice`: errors de validació](#advice)
   - [9.3 L'alternativa: excepcions pròpies](#problemdetail)
10. [CRUD complet: `Estudi`](#crud)
11. [Cap a Catan](#catan)
12. [Errors típics](#errors)

---

<a id="rest"></a>
## 1. Què és una API REST

Una **API** (*Application Programming Interface*) és la porta per la qual un programa fa servir un altre programa. Una **API web** ho fa amb peticions HTTP: un client (una app mòbil, una web en JavaScript, un altre servidor, Postman) envia una petició i rep dades, normalment en **JSON**.

**REST** (*Representational State Transfer*) és un estil per dissenyar aquestes API. Les idees bàsiques:

| Principi | Què vol dir | Exemple |
|---|---|---|
| **Recursos** | L'API exposa "coses" (estudis, videojocs, partides), no funcions | `/api/estudis/3` és l'estudi 3 |
| **URL identifica, verb actua** | L'acció la diu el mètode HTTP, no l'URL | `DELETE /api/estudis/3`, no `GET /api/esborrarEstudi?id=3` |
| **Representació** | El recurs viatja en un format (JSON) | `{"id":3,"nom":"Nintendo"}` |
| **Sense estat** (*stateless*) | Cada petició porta tot el que cal. El servidor no recorda res del client entre peticions | Per això els serveis no guarden estat ([sessió 1, §8](Spring_Sessio1_Intro.md#di)) |
| **Codis d'estat** | El resultat es comunica amb el codi HTTP | `404` si no existeix, `201` si s'ha creat |

---

<a id="url"></a>
## 2. Dissenyar les URL

| Regla | Bé | Malament |
|---|---|---|
| Noms, no verbs | `POST /api/estudis` | `GET /api/add?name=...` |
| Plural per a col·leccions | `/api/estudis`, `/api/estudis/3` | `/api/estudi/3` |
| L'id a la ruta | `/api/videojocs/7` | `/api/videojocs?id=7` |
| Jerarquia per a recursos que en contenen d'altres | `/api/estudis/3/videojocs` | `/api/videojocsDeEstudi?estudi=3` |
| Filtres, ordre i paginació com a paràmetres | `/api/videojocs?categoria=RPG&ordre=nom` | `/api/videojocs/rpg/ordenatsPerNom` |
| Minúscules i guions si cal | `/api/fitxes-tecniques` | `/api/FitxesTecniques` |

**Accions que no són CRUD.** Hi ha operacions que no són "crear, llegir, modificar, esborrar un recurs": tirar els daus, construir, publicar. Es modelen com un `POST` a una subruta del recurs, amb un verb:

```
POST /api/partides/5/daus                    tira els daus a la partida 5
POST /api/jugadors/12/construir?tipus=POBLAT&posicio=31
POST /api/categories/RPG/descompte?import=5
```

És acceptable i habitual: el que no s'ha de fer és fer servir `GET` per a una acció que modifica dades.

**Comparació amb els exemples de l'any passat:**

| Abans | Ara |
|---|---|
| `GET /ies/modul/add?name=DAW&id=1&horesSetmana=6` | `POST /api/moduls` amb el JSON al cos |
| `GET /ies/modul/user/1` | `GET /api/moduls/1` |
| `GET /ies/modul/all` | `GET /api/moduls` |
| `GET /ies/professor/1/afegir?modul=3` | `POST /api/professors/1/moduls/3` |

---

<a id="metodes"></a>
## 3. Els mètodes HTTP

| Mètode | Per a què | Anotació | Cos a la petició | Segur | Idempotent |
|---|---|---|---|---|---|
| `GET` | Llegir | `@GetMapping` | No | Sí | Sí |
| `POST` | Crear, o executar una acció | `@PostMapping` | Sí | No | **No** |
| `PUT` | Substituir un recurs sencer | `@PutMapping` | Sí | No | Sí |
| `PATCH` | Modificar només alguns camps | `@PatchMapping` | Sí | No | No necessàriament |
| `DELETE` | Esborrar | `@DeleteMapping` | No | No | Sí |

- **Segur**: no modifica res al servidor. Un navegador, un cercador o una cache poden fer `GET` sense demanar permís. Per això **un `GET` no pot modificar dades**.
- **Idempotent**: fer-lo dues vegades deixa el servidor igual que fer-lo una. `DELETE /api/estudis/3` dues vegades: el segon ja no troba res, però l'estat final és el mateix. `POST /api/estudis` dues vegades crea **dos** estudis (o dona error el segon).

Totes les anotacions deriven de `@RequestMapping`:

```java
@GetMapping("/{id}")
// és el mateix que
@RequestMapping(path = "/{id}", method = RequestMethod.GET)
```

Una classe controlador pot tenir tants mètodes com calgui. El límit és el disseny: un controlador per recurs (o per agregat) és la norma.

---

<a id="codis"></a>
## 4. Els codis d'estat

| Codi | Constant | Quan |
|---|---|---|
| `200 OK` | `HttpStatus.OK` | Lectura correcta, o modificació que retorna el recurs |
| `201 Created` | `HttpStatus.CREATED` | `POST` que ha creat un recurs. Ha de portar la capçalera `Location` amb l'URL del nou recurs |
| `204 No Content` | `HttpStatus.NO_CONTENT` | Correcte, sense res a retornar (típic de `DELETE`) |
| `400 Bad Request` | `HttpStatus.BAD_REQUEST` | La petició està mal formada o no passa la validació |
| `404 Not Found` | `HttpStatus.NOT_FOUND` | El recurs no existeix |
| `405 Method Not Allowed` | | L'URL existeix però no amb aquest verb (el genera Spring) |
| `409 Conflict` | `HttpStatus.CONFLICT` | Xoca amb l'estat actual: duplicat, no és el teu torn, posició ocupada |
| `422 Unprocessable Content` | `HttpStatus.UNPROCESSABLE_ENTITY` (nom antic del mateix codi) | Dades ben formades però que no es poden processar (alternativa a 409 per a regles de negoci) |
| `500 Internal Server Error` | | Excepció no controlada. **Mai** l'hem de retornar a propòsit |

**Errors que es veuen sovint:**

| Malament | Per què | Bé |
|---|---|---|
| `HttpStatus.FOUND` per dir "l'he trobat" | `302 Found` vol dir **redirecció**: el client anirà a buscar la capçalera `Location` | `200 OK` |
| `200 OK` amb el cos `"ERROR: no existeix"` | El client ha de llegir el text per saber si ha anat bé | `404` + missatge |
| `204 No Content` per a una llista buida | Una llista buida és un resultat correcte | `200` + `[]` |
| `451 Unavailable For Legal Reasons` per a una regla de negoci | És per a censura legal | `409` o `422` |

---

<a id="rebre"></a>
## 5. Rebre dades

<a id="pathvariable"></a>
### 5.1 `@PathVariable`: part de la ruta

```java
// GET /api/estudis/3/videojocs/7
@GetMapping("/{idEstudi}/videojocs/{idVideojoc}")
public ResponseEntity<?> buscar(@PathVariable int idEstudi, @PathVariable int idVideojoc) { ... }
```

El nom entre `{}` ha de coincidir amb el del paràmetre Java (o indicar-lo: `@PathVariable("idEstudi") int id`). Si el valor no es pot convertir (`/api/estudis/abc` amb un `int`), Spring respon `400`.

<a id="requestparam"></a>
### 5.2 `@RequestParam`: paràmetres de consulta

```java
// GET /api/videojocs?categoria=RPG&pagina=2
@GetMapping
public ResponseEntity<List<VideojocResum>> llistar(
        @RequestParam(required = false) String categoria,      // pot faltar -> null
        @RequestParam(defaultValue = "0") int pagina) {        // pot faltar -> 0
    ...
}
```

| Opció | Efecte |
|---|---|
| Res | Obligatori. Si falta: `400` |
| `required = false` | Opcional: arriba `null` (no feu servir tipus primitius: `int` no pot ser `null`) |
| `defaultValue = "..."` | Opcional amb valor per defecte (implica `required = false`) |

<a id="requestbody"></a>
### 5.3 `@RequestBody`: un JSON al cos de la petició

```java
// POST /api/estudis
// Content-Type: application/json
// {"nom": "Supergiant Games"}
@PostMapping
public ResponseEntity<?> crear(@Valid @RequestBody EstudiPeticio peticio) { ... }
```

Jackson converteix el JSON en un objecte Java fent coincidir **els noms dels camps**. Si el JSON està mal format, Spring respon `400`. Si un camp del JSON no existeix a la classe, per defecte s'ignora.

**Un JSON amb una llista** (com el `POST /partida` de Catan):

```json
{ "jugadors": ["Anna", "Pau", "Marta"], "puntsPerGuanyar": 10 }
```

```java
public record PartidaPeticio(List<String> jugadors, Integer puntsPerGuanyar) { }
```

<a id="quan"></a>
### 5.4 Quan fer servir cadascun

| Què | On | Exemple |
|---|---|---|
| **Quin** recurs | `@PathVariable` | `/api/jugadors/{id}` |
| Filtres, ordre, opcions | `@RequestParam` | `?categoria=RPG`, `?tipus=POBLAT&posicio=31` |
| Les **dades** d'un recurs nou o modificat | `@RequestBody` | `{"nom": "..."}` |
| Metadades (autenticació, idioma...) | `@RequestHeader` | `@RequestHeader("Accept-Language") String idioma` |

---

<a id="retornar"></a>
## 6. Retornar dades: `ResponseEntity`

Un mètode d'un `@RestController` pot retornar:

| Retorn | Què envia |
|---|---|
| Un objecte o una llista | `200` + l'objecte en JSON |
| `String` | `200` + el text tal qual (`text/plain`) |
| `void` | `200` sense cos |
| **`ResponseEntity<T>`** | **El que triem**: codi, capçaleres i cos |

A classe, el `ResponseEntity` el construeix el **servei** (sessió 1, [§9.2](Spring_Sessio1_Intro.md#csr)) i el controlador el retorna tal qual. `ResponseEntity` té mètodes per als casos habituals:

```java
ResponseEntity.ok().body(objecte)                           // 200 + cos
ResponseEntity.created(uri).body(objecte)                   // 201 + capçalera Location + cos
ResponseEntity.noContent().build()                          // 204 sense cos
ResponseEntity.badRequest().body("missatge")                // 400 + cos
ResponseEntity.status(HttpStatus.NOT_FOUND).body("missatge")// qualsevol codi

// Amb la capçalera pròpia
ResponseEntity.status(HttpStatus.CONFLICT)
		.header("Header-Customitzat", "ESTUDI DUPLICAT")
		.body("ERROR: JA EXISTEIX L'ESTUDI " + nom);
```

### El tipus del cos: `ResponseEntity<T>` o `ResponseEntity<?>`

| Tipus | Quan | Exemple |
|---|---|---|
| `ResponseEntity<List<Estudi>>` | El cos sempre és del mateix tipus | `llistar()`: sempre una llista (potser buida) |
| `ResponseEntity<String>` | El cos sempre és un text | `esborrar()`: un missatge o res |
| `ResponseEntity<?>` | El cos **depèn del resultat** | `buscar()`: l'`Estudi` si el troba, un `String` amb l'error si no |

El `?` vol dir "qualsevol tipus". Així, en un error, el client rep el missatge al cos en lloc d'un cos buit (`body(null)`).

### La capçalera pròpia

`Header-Customitzat` porta informació extra per a qui consumeix l'API (`"Total estudis: 3"`, `"ID 99 NO TROBAT"`). Es veu a la pestanya *Headers* de Postman o amb `curl -i`. Dues regles:

- El **missatge principal va al cos**. La capçalera és informació complementària.
- **Sense accents** a les capçaleres: HTTP no les pensa per a caràcters fora de l'ASCII i poden arribar malmeses. Els accents, al cos.

---

<a id="dto"></a>
## 7. DTO: no exposar les entitats

Un **DTO** (*Data Transfer Object*) és una classe que només serveix per transportar dades d'entrada o de sortida de l'API. N'hi ha de dos tipus:

| | DTO de **petició** | DTO de **resposta** |
|---|---|---|
| Què és | El JSON que **envia** el client (`@RequestBody`) | El JSON que **retorna** l'API |
| Exemple | `EstudiPeticio`, `VideojocPeticio` | `VideojocResum`, `VideojocResposta` |
| Avui | **Sí** | A la sessió 3, quan apareixen les relacions |

### Per què no rebre l'entitat al `@RequestBody`

```java
@PostMapping
public ResponseEntity<?> crear(@RequestBody Estudi estudi) { ... }   // NO
```

| Problema | Amb `EstudiPeticio` |
|---|---|
| El client pot enviar camps que no hauria de tocar: `{"id": 1, "nom": "..."}` sobreescriuria l'estudi 1 | Només té `nom` |
| Les validacions de l'API (`@NotBlank`) es barregen amb les de la BD a l'entitat | Cada petició té les seves |
| La petició no sempre té la forma de l'entitat: per publicar un videojoc, l'estudi arriba **pel nom** (`"estudi": "Nintendo"`), no com un objecte `Estudi` | El DTO té la forma de la petició |
| Canviar l'entitat canvia l'API i trenca els clients | L'API és estable |

### Records

Amb Java 16+ els DTO es fan amb **`record`**: immutables, amb constructor, accessors (`nom()`, no `getNom()`), `equals`, `hashCode` i `toString` automàtics. Jackson els entén directament.

```java
package cat.iesabadell.videojocs.dto;

/*
 * DTO de PETICIÓ: el JSON que el client pot enviar per crear o modificar un estudi.
 *   {"nom": "Supergiant Games"}
 * Només té els camps que el client pot tocar: ni l'id ni la llista de videojocs.
 */
public record EstudiPeticio(String nom) {
}
```

Equival a una classe amb un atribut `private final String nom`, el constructor, el mètode `nom()`, `equals`, `hashCode` i `toString`. Al servei: `peticio.nom()`.

> **Records i JPA**: un `record` **no** pot ser una `@Entity` (és immutable i no té constructor buit). Els records són per als DTO i per a les projeccions de consultes (sessió 4).

### I la resposta?

`Estudi` es pot retornar directament perquè la seva única relació (`videojocs`) porta `@JsonIgnore`: Jackson se la salta. És l'opció senzilla quan una entitat té poques relacions i cap fa falta a la resposta. A la sessió 3 veurem per què amb `Videojoc` no n'hi ha prou i cal un DTO de resposta.

```java
@OneToMany(mappedBy="estudi", cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.LAZY)
@JsonIgnore
private List<Videojoc> videojocs = new ArrayList<>();
```

---

<a id="validar"></a>
## 8. Validar l'entrada

Les mateixes anotacions de **Bean Validation** que vam veure a la sessió 1 d'Hibernate (`@NotBlank`, `@Size`, `@Min`, `@Max`, `@Positive`, `@Email`, `@Pattern`...). Cal l'starter `spring-boot-starter-validation` ([sessió 1, §3](Spring_Sessio1_Intro.md#crear)).

Al DTO de petició:

```java
public record EstudiPeticio(
		@NotBlank(message = "El nom és obligatori")
		@Size(max = 50, message = "El nom no pot passar de 50 caràcters")
		String nom) {
}
```

I `@Valid` al paràmetre del controlador:

```java
@PostMapping
public ResponseEntity<?> crear(@Valid @RequestBody EstudiPeticio peticio) {
	return service.crear(peticio);
}
```

Sense `@Valid` les anotacions **no fan res**. Amb `@Valid`, si alguna falla, Spring **no crida el mètode** (ni, per tant, el servei) i llança `MethodArgumentNotValidException`, que gestionem a [§9.2](#advice).

**Validar `@PathVariable` i `@RequestParam`** amb una anotació directament al paràmetre (llança `HandlerMethodValidationException`):

```java
@GetMapping(path="/pagina")
public ResponseEntity<PaginaResposta<VideojocResum>> pagina(
		@RequestParam(defaultValue = "0") @Min(value = 0, message = "La pàgina comença a 0") int pagina,
		@RequestParam(defaultValue = "5") @Max(value = 50, message = "La mida màxima és 50") int mida) {
	return service.pagina(pagina, mida);
}
```

**Tres nivells, com a Hibernate:**

| On | Què valida | Exemple |
|---|---|---|
| DTO amb `@Valid` | La forma de la petició | El nom no és buit |
| Servei | Les regles de negoci, que necessiten la BD | El nom no està repetit; no és el teu torn |
| Entitat i BD | L'última barrera | `@Column(unique=true)`, `nullable=false` |

---

<a id="errors-api"></a>
## 9. Gestió d'errors

<a id="llancar"></a>
### 9.1 El servei decideix el codi

Cada mètode del servei comprova els casos d'error **abans** de fer la feina i, per a cadascun, retorna el `ResponseEntity` amb el codi que toca. El camí bo va al final:

```java
public ResponseEntity<String> esborrar(int id) {
	Optional<Estudi> e = repository.findById(id);
	if (!e.isPresent()) {                                         // 1. no existeix -> 404
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.header("Header-Customitzat", "ID " + id + " NO TROBAT")
				.body("NO EXISTEIX CAP ESTUDI AMB ID " + id);
	}
	if (vRepository.existsByEstudiId(id)) {                       // 2. regla de negoci -> 409
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.header("Header-Customitzat", "ESTUDI AMB VIDEOJOCS")
				.body("ERROR: L'ESTUDI " + e.get().getNom() + " TÉ VIDEOJOCS I NO ES POT ESBORRAR");
	}
	repository.delete(e.get());                                   // 3. camí bo -> 204
	return ResponseEntity.noContent()
			.header("Header-Customitzat", "Estudi " + id + " esborrat")
			.build();
}
```

| Cas | Codi |
|---|---|
| El recurs no existeix | `404` |
| Duplicat, regla de negoci ("té videojocs", "no és el teu torn") | `409` |
| Creat | `201` (+ `Location`) |
| Esborrat | `204` |
| Llegit o modificat | `200` |

Els errors de **validació** són diferents: no arriben mai al servei, perquè `@Valid` atura la petició abans. Per a aquests cal un gestor global.

<a id="advice"></a>
### 9.2 `@RestControllerAdvice`: errors de validació

Una classe que intercepta les excepcions de **tots** els controladors i en fa la resposta:

```java
package cat.iesabadell.videojocs.exception;

@RestControllerAdvice
public class GestorErrors {

	// @Valid @RequestBody ha fallat -> 400 amb un JSON {camp: missatge}
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<LinkedHashMap<String, String>> validacioCos(MethodArgumentNotValidException e) {
		LinkedHashMap<String, String> errors = new LinkedHashMap<>();
		for (FieldError fe : e.getBindingResult().getFieldErrors()) {
			errors.put(fe.getField(), fe.getDefaultMessage());
		}
		return ResponseEntity.badRequest()
				.header("Header-Customitzat", "DADES NO VALIDES")
				.body(errors);
	}

	// Una validació d'un @PathVariable o @RequestParam ha fallat (per exemple, @Positive) -> 400
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<String> validacioParametres(HandlerMethodValidationException e) {
		String missatge = "";
		for (MessageSourceResolvable error : e.getAllErrors()) {
			missatge += error.getDefaultMessage() + "\n";
		}
		return ResponseEntity.badRequest()
				.header("Header-Customitzat", "PARAMETRES NO VALIDS")
				.body(missatge);
	}

}
```

Resposta d'un `POST /api/estudis` amb `{"nom": ""}`:

```json
{ "nom": "El nom és obligatori" }
```

Ha de ser a sota del paquet arrel, com tots els beans.

<a id="problemdetail"></a>
### 9.3 L'alternativa: excepcions pròpies

Molts projectes ho fan a l'inrevés: el servei **retorna dades** (l'`Estudi`, no un `ResponseEntity`) i, quan alguna cosa falla, **llança una excepció pròpia**. El `@RestControllerAdvice` converteix cada excepció en una resposta:

```java
// Servei
public Estudi buscar(int id) {
	return repository.findById(id)
			.orElseThrow(() -> new NoTrobatException("NO EXISTEIX CAP ESTUDI AMB ID " + id));
}

// GestorErrors
@ExceptionHandler(NoTrobatException.class)
public ResponseEntity<String> noTrobat(NoTrobatException e) {
	return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
}
```

| | El servei retorna `ResponseEntity` (classe) | Excepcions pròpies |
|---|---|---|
| On es decideix el codi | Al servei, cas per cas | Al gestor global, per tipus d'excepció |
| El servei es pot fer servir fora de l'API (vistes, un altre servei) | Cal treure el cos del `ResponseEntity` (`getBody()`) | Sí, retorna dades |
| Rollback en un error dins d'una transacció | Cal marcar-lo (sessió 3, [§2.4](Spring_Sessio3_Relacions_Transaccions.md#tx-rollback)) | Automàtic (és una `RuntimeException`) |
| Codi | Tot visible en un sol mètode | Més repartit, menys repetit |

Spring també porta un format estàndard per als errors (`ProblemDetail`, RFC 9457) que es pot fer servir en qualsevol dels dos estils. Ho deixem per a qui vulgui ampliar.

---

<a id="crud"></a>
## 10. CRUD complet: `Estudi`

El codi és el del projecte `videojocsSpring`.

Regles de negoci:
- No hi pot haver dos estudis amb el mateix nom.
- **No es pot esborrar un estudi que té videojocs.** Ull: `Estudi.videojocs` té `cascade = ALL` i `orphanRemoval`. Si l'esborréssim sense comprovar-ho, s'esborrarien tots els seus videojocs.

### Repositoris

```java
public interface EstudiRepository extends JpaRepository<Estudi, Integer> {

	Optional<Estudi> findByNom(String nom);

	boolean existsByNom(String nom);

	// Hi ha un ALTRE estudi (id diferent) amb aquest nom? Per al PUT
	boolean existsByNomAndIdNot(String nom, Integer id);

}
```

```java
public interface VideojocRepository extends JpaRepository<Videojoc, Integer> {

	// Navega per la relació: videojoc.estudi.id (només mira la FK)
	boolean existsByEstudiId(Integer idEstudi);

	long countByEstudiId(Integer idEstudi);

}
```

### Servei

```java
@Service
public class EstudiService {

	@Autowired
	private EstudiRepository repository;

	@Autowired
	private VideojocRepository vRepository;

	public ResponseEntity<List<Estudi>> llistar() {
		List<Estudi> estudis = repository.findAll(Sort.by("nom"));
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Total estudis: " + estudis.size())
				.body(estudis);
	}

	public ResponseEntity<?> buscar(int id) {
		Optional<Estudi> e = repository.findById(id);
		if (e.isPresent()) {
			return ResponseEntity.ok()
					.header("Header-Customitzat", "Dades estudi ID: " + id)
					.body(e.get());
		} else {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.header("Header-Customitzat", "ID " + id + " NO TROBAT")
					.body("NO EXISTEIX CAP ESTUDI AMB ID " + id);
		}
	}

	public ResponseEntity<?> crear(EstudiPeticio peticio) {
		if (repository.existsByNom(peticio.nom())) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.header("Header-Customitzat", "ESTUDI DUPLICAT")
					.body("ERROR: JA EXISTEIX L'ESTUDI " + peticio.nom());
		}
		Estudi e = repository.save(new Estudi(peticio.nom()));
		// 201 Created + capçalera Location amb l'URL del recurs nou
		return ResponseEntity.created(URI.create("/api/estudis/" + e.getId()))
				.header("Header-Customitzat", "Estudi creat amb ID: " + e.getId())
				.body(e);
	}

	/*
	 * @Transactional: l'estudi que llegim és GESTIONAT fins al final del mètode.
	 * Amb setNom n'hi ha prou: Hibernate fa l'UPDATE en fer commit (dirty checking), sense save.
	 */
	@Transactional
	public ResponseEntity<?> modificar(int id, EstudiPeticio peticio) {
		Optional<Estudi> e = repository.findById(id);
		if (!e.isPresent()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.header("Header-Customitzat", "ID " + id + " NO TROBAT")
					.body("NO EXISTEIX CAP ESTUDI AMB ID " + id);
		}
		if (repository.existsByNomAndIdNot(peticio.nom(), id)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.header("Header-Customitzat", "ESTUDI DUPLICAT")
					.body("ERROR: JA EXISTEIX UN ALTRE ESTUDI AMB EL NOM " + peticio.nom());
		}
		e.get().setNom(peticio.nom());
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Estudi " + id + " modificat")
				.body(e.get());
	}

	public ResponseEntity<String> esborrar(int id) {
		// el de §9.1
	}

}
```

> `@Transactional` i el *dirty checking* s'expliquen a fons a la sessió 3. De moment: dins d'un mètode `@Transactional`, modificar una entitat que hem llegit és suficient perquè es desi.

### Controlador

```java
@RestController
@RequestMapping(path="/api/estudis")
public class EstudiController {

	@Autowired
	private EstudiService service;

	// GET localhost:8080/api/estudis
	@GetMapping
	public ResponseEntity<List<Estudi>> llistar() {
		return service.llistar();
	}

	// GET localhost:8080/api/estudis/1
	@GetMapping(path="/{id}")
	public ResponseEntity<?> buscar(@PathVariable int id) {
		return service.buscar(id);
	}

	/*
	 * POST localhost:8080/api/estudis
	 * Content-Type: application/json   {"nom": "Valve"}
	 * @RequestBody: Jackson converteix el JSON del cos en un EstudiPeticio
	 * @Valid: comprova @NotBlank i @Size ABANS d'entrar al mètode. Si falla -> GestorErrors -> 400
	 */
	@PostMapping
	public ResponseEntity<?> crear(@Valid @RequestBody EstudiPeticio peticio) {
		return service.crear(peticio);
	}

	// PUT localhost:8080/api/estudis/1   {"nom": "Nintendo EPD"}
	@PutMapping(path="/{id}")
	public ResponseEntity<?> modificar(@PathVariable int id, @Valid @RequestBody EstudiPeticio peticio) {
		return service.modificar(id, peticio);
	}

	// DELETE localhost:8080/api/estudis/4
	@DeleteMapping(path="/{id}")
	public ResponseEntity<String> esborrar(@PathVariable int id) {
		return service.esborrar(id);
	}

}
```

### Casos de prova

Amb les dades dels CSV (CD Projekt Red = 1, Supergiant Games = 2, Nintendo = 3):

| Petició | Cos | Resposta |
|---|---|---|
| `POST /api/estudis` | `{"nom": "Valve"}` | `201`, `Location: /api/estudis/4`, `{"id":4,"nom":"Valve"}` |
| `POST /api/estudis` | `{"nom": "Nintendo"}` | `409` · `ERROR: JA EXISTEIX L'ESTUDI Nintendo` |
| `POST /api/estudis` | `{"nom": ""}` | `400` · `{"nom":"El nom és obligatori"}` |
| `POST /api/estudis` | `{"nom": "Valve"` (falta `}`) | `400` (JSON mal format) |
| `GET /api/estudis` | | `200` · els quatre estudis per ordre alfabètic |
| `GET /api/estudis/99` | | `404` · `NO EXISTEIX CAP ESTUDI AMB ID 99` |
| `PUT /api/estudis/1` | `{"nom": "CD PROJEKT RED"}` | `200` · `{"id":1,"nom":"CD PROJEKT RED"}` |
| `DELETE /api/estudis/1` | | `409` · té videojocs |
| `DELETE /api/estudis/4` | | `204` |

---

<a id="catan"></a>
## 11. Cap a Catan

| Endpoint de l'enunciat | Peces d'avui |
|---|---|
| `GET /partida/{idPartida}/tauler` → array JSON | `@PathVariable` ([§5.1](#pathvariable)); llista de DTO ([§7](#dto)) |
| `POST /partida` amb la llista de jugadors i els punts opcionals | `@RequestBody` amb un `record` ([§5.3](#requestbody)); `201` ([§6](#retornar)) |
| `POST /jugador/{idJugador}/construir?tipus=...&posicio=...` → un text diferent per a cada cas | `@PathVariable` + `@RequestParam` ([§5.4](#quan)). Cada cas, un `ResponseEntity<String>` amb el seu codi i el seu text ([§9.1](#llancar)): "no és el teu torn", "posició ocupada"... → `409`; "sense recursos" → `409` o `422` |
| `POST /partida/{idPartida}/daus` → JSON amb la tirada i els recursos rebuts | Acció com a `POST` a una subruta ([§2](#url)); DTO de resposta ([§7](#dto)) |
| `GET /jugador/{idJugador}/recursos` → 404 si no existeix | `ResponseEntity<?>` ([§6](#retornar), [§9.1](#llancar)) |
| Document de proves | Col·lecció de Postman o captures de Swagger ([§11](#provar)) |

> Les rutes de Catan són les de l'enunciat (en singular). Seguiu l'enunciat; les regles de [§2](#url) són les que fareu servir quan dissenyeu una API vosaltres.

---

<a id="errors"></a>
## 12. Errors típics

| Error | Causa | Solució |
|---|---|---|
| `415 Unsupported Media Type` | Falta `Content-Type: application/json` a la petició | Postman: *Body → raw → JSON*. `curl`: `-H "Content-Type: application/json"` |
| `400` amb `JSON parse error` | JSON mal format (cometes simples, coma final, falta `}`) | Revisar el JSON |
| Els camps del `@RequestBody` arriben a `null` | Els noms del JSON no coincideixen amb els del DTO (`"Nom"` vs `nom`) | Mateixos noms, mateixes majúscules |
| Les validacions no s'apliquen | Falta `@Valid`, o falta l'starter de validació | [§8](#validar) |
| `500` (`NoSuchElementException`) en lloc de `404` | El servei fa `findById(id).get()` i l'`Optional` és buit | `isPresent()` i `404` ([§9.1](#llancar)) |
| Error de validació amb resposta per defecte (sense el JSON de camps) | El `GestorErrors` és fora del paquet arrel, o no té `@RestControllerAdvice` | [§9.2](#advice) |
| Al cos de l'error surt `null` | `ResponseEntity<Estudi>` amb `.body(null)` en el cas d'error | `ResponseEntity<?>` i el missatge al cos ([§6](#retornar)) |
| Caràcters estranys a `Header-Customitzat` | Accents a la capçalera | Sense accents; el missatge al cos ([§6](#retornar)) |
| `HttpMessageNotWritableException` o JSON infinit | Es retorna una entitat amb relacions bidireccionals | DTO ([§7](#dto)). Sessió 3 |
| `HttpMessageNotWritableException: No serializer found for class ...` | Es retorna un objecte sense getters públics | Afegir getters, o fer servir un `record` |
| Funciona amb Postman però no des d'una web | CORS | `@CrossOrigin` ([§12](#consumir)) |
| `405 Method Not Allowed` | Verb equivocat (`GET` a un `@PostMapping`) | [§3](#metodes) |

---
