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
10. [L'API del projecte: plataformes i videojocs](#crud)
11. [Cap a Catan](#catan)
12. [Errors típics](#errors)

---

<a id="rest"></a>
## 1. Què és una API REST

Una **API** (*Application Programming Interface*) és la porta per la qual un programa fa servir un altre programa. Una **API web** ho fa amb peticions HTTP: un client (una app mòbil, una web en JavaScript, un altre servidor, Postman) envia una petició i rep dades, normalment en **JSON**.

**REST** (*Representational State Transfer*) és un estil per dissenyar aquestes API. Les idees bàsiques:

| Principi | Què vol dir | Exemple |
|---|---|---|
| **Recursos** | L'API exposa "coses" (estudis, videojocs, partides), no funcions | `/api/videojocs/3` és el videojoc 3 |
| **URL identifica, verb actua** | L'acció la diu el mètode HTTP, no l'URL | `DELETE /api/videojocs/3`, no `GET /api/esborrarVideojoc?id=3` |
| **Representació** | El recurs viatja en un format (JSON) | `{"id":1,"nom":"PC"}` |
| **Sense estat** (*stateless*) | Cada petició porta tot el que cal. El servidor no recorda res del client entre peticions | Per això els serveis no guarden estat ([sessió 1, §8](Spring_Sessio1_Intro.md#di)) |
| **Codis d'estat** | El resultat es comunica amb el codi HTTP | `404` si no existeix, `201` si s'ha creat |

---

<a id="url"></a>
## 2. Dissenyar les URL

| Regla | Bé | Malament |
|---|---|---|
| Noms, no verbs | `POST /api/videojocs` | `GET /api/add?name=...` |
| Plural per a col·leccions | `/api/plataformes`, `/api/plataformes/3` | `/api/plataforma/3` |
| L'id a la ruta | `/api/videojocs/7` | `/api/videojocs?id=7` |
| Jerarquia per a recursos que en contenen d'altres | `/api/videojocs/3/llancaments` | `/api/llancamentsDeVideojoc?videojoc=3` |
| Filtres, ordre i paginació com a paràmetres | `/api/videojocs?categoria=RPG&ordre=nom` | `/api/videojocs/rpg/ordenatsPerNom` |
| Minúscules i guions si cal | `/api/fitxes-tecniques` | `/api/FitxesTecniques` |

**Accions que no són CRUD.** Hi ha operacions que no són "crear, llegir, modificar, esborrar un recurs": tirar els daus, construir, publicar. Es modelen com un `POST` a una subruta del recurs, amb un verb:

```
POST /api/partides/5/daus                    tira els daus a la partida 5
POST /api/jugadors/12/construir?tipus=POBLAT&posicio=31
POST /api/videojocs/descompte?categoria=RPG&import=5
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
- **Idempotent**: fer-lo dues vegades deixa el servidor igual que fer-lo una. `DELETE /api/videojocs/3` dues vegades: el segon ja no troba res, però l'estat final és el mateix. `POST /api/plataformes?nom=Xbox` dues vegades no: la primera crea la plataforma i la segona dona un `409`.

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
// POST /api/videojocs/3/categories/RPG
@PostMapping("/{id}/categories/{categoria}")
public ResponseEntity<?> afegirCategoria(@PathVariable int id, @PathVariable String categoria) { ... }
```

El nom entre `{}` ha de coincidir amb el del paràmetre Java (o indicar-lo: `@PathVariable("id") int idVideojoc`). Si el valor no es pot convertir (`/api/videojocs/abc` amb un `int`), Spring respon `400`.

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
// POST /api/videojocs
// Content-Type: application/json
// {"nom": "Hades II", "estudi": "Supergiant Games", "motor": "Motor propi",
//  "categories": ["Roguelike"], "preus": {"PC": 29.99, "Switch": 29.99}}
@PostMapping
public ResponseEntity<?> publicar(@Valid @RequestBody VideojocPeticio peticio) { ... }
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
		.header("Header-Customitzat", "VIDEOJOC DUPLICAT")
		.body("ERROR: JA EXISTEIX EL VIDEOJOC " + nom);
```

### El tipus del cos: `ResponseEntity<T>` o `ResponseEntity<?>`

| Tipus | Quan | Exemple |
|---|---|---|
| `ResponseEntity<List<Plataforma>>` | El cos sempre és del mateix tipus | `llistar()`: sempre una llista (potser buida) |
| `ResponseEntity<String>` | El cos sempre és un text | `esborrar()`: un missatge o res |
| `ResponseEntity<?>` | El cos **depèn del resultat** | `buscar()`: la `Plataforma` si la troba, un `String` amb l'error si no |

El `?` vol dir "qualsevol tipus". Així, en un error, el client rep el missatge al cos en lloc d'un cos buit (`body(null)`).

### La capçalera pròpia

`Header-Customitzat` porta informació extra per a qui consumeix l'API (`"Total plataformes: 3"`, `"ID 99 NO TROBAT"`). Es veu a la pestanya *Headers* de Postman o amb `curl -i`. Dues regles:

- El **missatge principal va al cos**. La capçalera és informació complementària.
- **Sense accents** a les capçaleres: HTTP no les pensa per a caràcters fora de l'ASCII i poden arribar malmeses. Els accents, al cos.

---

<a id="dto"></a>
## 7. DTO: no exposar les entitats

Un **DTO** (*Data Transfer Object*) és una classe que només serveix per transportar dades d'entrada o de sortida de l'API. N'hi ha de dos tipus:

| | DTO de **petició** | DTO de **resposta** |
|---|---|---|
| Què és | El JSON que **envia** el client (`@RequestBody`) | El JSON que **retorna** l'API |
| Exemple | `VideojocPeticio` | `VideojocResum`, `VideojocResposta` |
| Avui | **Sí** | A la sessió 3, quan apareixen les relacions |

### Per què no rebre l'entitat al `@RequestBody`

```java
@PostMapping
public ResponseEntity<?> publicar(@RequestBody Videojoc videojoc) { ... }   // NO
```

| Problema | Amb `VideojocPeticio` |
|---|---|
| El client pot enviar camps que no hauria de tocar: `{"id": 1, ...}` sobreescriuria el videojoc 1 | No té `id` |
| Les validacions de l'API (`@NotBlank`) es barregen amb les de la BD a l'entitat | Cada petició té les seves |
| La petició no té la forma de l'entitat: l'estudi, les categories i les plataformes arriben **pel nom** (`"estudi": "Nintendo"`), no com a objectes `Estudi` o `Categoria` | El DTO té la forma de la petició |
| Canviar l'entitat canvia l'API i trenca els clients | L'API és estable |

### Records

Amb Java 16+ els DTO es fan amb **`record`**: immutables, amb constructor, accessors (`nom()`, no `getNom()`), `equals`, `hashCode` i `toString` automàtics. Jackson els entén directament.

```java
package cat.iesabadell.videojocs.dto;

/*
 * DTO de PETICIÓ per publicar un videojoc complet. La forma és la que li va bé al client,
 * no la de les taules: l'estudi, les categories i les plataformes van pel NOM.
 * Les validacions s'hi afegeixen a §8.
 */
public record VideojocPeticio(String nom, String estudi, String motor,
		List<String> categories, Map<String, BigDecimal> preus) {
}
```

```json
{
  "nom": "Hades II",
  "estudi": "Supergiant Games",
  "motor": "Motor propi",
  "categories": ["Roguelike"],
  "preus": { "PC": 29.99, "Switch": 29.99 }
}
```

Equival a una classe amb un atribut `private final` per a cada camp, el constructor, els accessors (`nom()`, `estudi()`, `categories()`...), `equals`, `hashCode` i `toString`. Al servei: `peticio.nom()`, `peticio.preus()`.

> **Records i JPA**: un `record` **no** pot ser una `@Entity` (és immutable i no té constructor buit). Els records són per als DTO i per a les projeccions de consultes (sessió 4).

### I la resposta?

`Plataforma` es pot retornar directament: no té relacions pròpies i Jackson la converteix tal qual (`{"id":1,"nom":"PC"}`). És el que fa `PlataformaService` des de la sessió 1. `Videojoc` no: té relacions, i a la sessió 3 veurem per què cal un DTO de resposta.

---

<a id="validar"></a>
## 8. Validar l'entrada

Les mateixes anotacions de **Bean Validation** que vam veure a la sessió 1 d'Hibernate (`@NotBlank`, `@Size`, `@Min`, `@Max`, `@Positive`, `@Email`, `@Pattern`...). Cal l'starter `spring-boot-starter-validation` ([sessió 1, §3](Spring_Sessio1_Intro.md#crear)).

Al DTO de petició:

```java
public record VideojocPeticio(
		@NotBlank(message = "El nom és obligatori")
		String nom,

		@NotBlank(message = "L'estudi és obligatori")
		String estudi,

		// Opcional: si no hi és, el videojoc no té fitxa tècnica
		String motor,

		@NotNull(message = "Cal la llista de categories (pot ser buida)")
		List<String> categories,

		// Clau: nom de la plataforma. Valor: preu (ha de ser positiu)
		@NotNull(message = "Cal el mapa de preus (pot ser buit)")
		Map<String, @Positive(message = "Els preus han de ser positius") BigDecimal> preus) {
}
```

Fixeu-vos en `Map<String, @Positive BigDecimal>`: la validació s'aplica a **cada valor** del mapa.

I `@Valid` al paràmetre del controlador:

```java
@PostMapping
public ResponseEntity<?> publicar(@Valid @RequestBody VideojocPeticio peticio) {
	return service.publicar(peticio);
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
// PlataformaService
public ResponseEntity<String> esborrar(int id) {
	Optional<Plataforma> p = repository.findById(id);
	if (!p.isPresent()) {                                         // 1. no existeix -> 404
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.header("Header-Customitzat", "ID " + id + " NO TROBAT")
				.body("NO EXISTEIX CAP PLATAFORMA AMB ID " + id);
	}
	if (lRepository.existsByPlataformaId(id)) {                   // 2. regla de negoci -> 409
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.header("Header-Customitzat", "PLATAFORMA AMB LLANCAMENTS")
				.body("ERROR: " + p.get().getNom() + " TE LLANÇAMENTS I NO ES POT ESBORRAR");
	}
	repository.delete(p.get());                                   // 3. camí bo -> 204
	return ResponseEntity.noContent()
			.header("Header-Customitzat", "Plataforma " + id + " esborrada")
			.build();
}
```

| Cas | Codi |
|---|---|
| El recurs no existeix | `404` |
| Duplicat, regla de negoci ("té llançaments", "no és el teu torn") | `409` |
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

Resposta d'un `POST /api/videojocs` amb `{"nom": "", "estudi": "Nintendo", "categories": [], "preus": {}}`:

```json
{ "nom": "El nom és obligatori" }
```

Ha de ser a sota del paquet arrel, com tots els beans.

<a id="problemdetail"></a>
### 9.3 L'alternativa: excepcions pròpies

Molts projectes ho fan a l'inrevés: el servei **retorna dades** (la `Plataforma`, no un `ResponseEntity`) i, quan alguna cosa falla, **llança una excepció pròpia**. El `@RestControllerAdvice` converteix cada excepció en una resposta:

```java
// Servei
public Plataforma buscar(int id) {
	return repository.findById(id)
			.orElseThrow(() -> new NoTrobatException("NO EXISTEIX CAP PLATAFORMA AMB ID " + id));
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
## 10. L'API del projecte: plataformes i videojocs

El codi és el del projecte `videojocsSpring`. Hi ha dos controladors:

| Controlador | Servei | Què és |
|---|---|---|
| `PlataformaController` (`/api/plataformes`) | `PlataformaService` | El CRUD de la sessió 1, ara amb l'esborrat |
| `VideojocController` (`/api/videojocs`) | `VideojocService` | El **servei fort** (sessió 3): tot el que toca l'agregat Videojoc |

No hi ha `EstudiController` ni `CategoriaController`. Com al DAO fort, les altres entitats no tenen API pròpia: es gestionen des de Videojoc. L'estudi i les categories es busquen **pel nom**; la fitxa tècnica i els llançaments es desen **en cascada** des del videojoc.

### Plataformes: el CRUD

Regles de negoci:
- No hi pot haver dues plataformes amb el mateix nom.
- **No es pot esborrar una plataforma que té llançaments.** La FK de `llancament` ho impediria amb un error de BD poc clar: es comprova abans i es respon `409` ([§9.1](#llancar)).

```java
public interface PlataformaRepository extends JpaRepository<Plataforma, Integer> {

	Optional<Plataforma> findByNom(String nom);

	boolean existsByNom(String nom);

	// ... les consultes derivades de la sessió 1 i les de la sessió 4

}

public interface LlancamentRepository extends JpaRepository<Llancament, Integer> {

	// Navega per la relació: llancament.plataforma.id (només mira la FK)
	boolean existsByPlataformaId(Integer idPlataforma);

	// ...

}
```

`llistar` i `buscar` són els de la sessió 1, i `esborrar` és el de [§9.1](#llancar). El `crear`:

```java
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
```

```java
// PlataformaController
// POST localhost:8080/api/plataformes?nom=Xbox
@PostMapping
public ResponseEntity<String> crear(@RequestParam String nom) {
	return service.crear(nom);
}

// DELETE localhost:8080/api/plataformes/4
@DeleteMapping(path="/{id}")
public ResponseEntity<String> esborrar(@PathVariable int id) {
	return service.esborrar(id);
}
```

> A la sessió 1 el nom arriba per `@RequestParam` perquè es pugui provar fàcilment. Les dades d'un recurs amb més camps arriben com a JSON amb `@RequestBody` i un DTO, com a `publicar`.

### Videojocs: les operacions del servei fort

| Mètode | URL | Paràmetres o cos | Resposta |
|---|---|---|---|
| `GET` | `/api/videojocs` | `?categoria=RPG` (opcional) | `200` + llista de `VideojocResum` |
| `GET` | `/api/videojocs/{id}` | | `200` + `VideojocResposta`, o `404` |
| `POST` | `/api/videojocs` | `VideojocPeticio` en JSON | `201` + `Location`, `400`, `404` o `409` |
| `POST` | `/api/videojocs/{id}/categories/{categoria}` | | `200`, `404` o `409` |
| `DELETE` | `/api/videojocs/{id}/categories/{categoria}` | | `200` o `404` |
| `POST` | `/api/videojocs/{id}/llancaments` | `?plataforma=PS5&preu=24.99` | `201`, `404` o `409` |
| `POST` | `/api/videojocs/descompte` | `?categoria=Acció&import=5` | `200` o `404` |
| `DELETE` | `/api/videojocs/{id}` | | `204` o `404` |

El controlador és curt: cada mètode llegeix els paràmetres i crida el servei.

```java
// VideojocController
// POST localhost:8080/api/videojocs   (el JSON de VideojocPeticio)
@PostMapping
public ResponseEntity<?> publicar(@Valid @RequestBody VideojocPeticio peticio) {
	return service.publicar(peticio);
}
```

El codi del servei i els DTO de resposta són a la sessió 3 ([§5](Spring_Sessio3_Relacions_Transaccions.md#servei)).

> No hi ha cap `PUT`: cap operació del DAO fort substituïa un recurs sencer. Si en calgués un, seguiria el mateix esquema: `@PutMapping("/{id}")` amb `@Valid @RequestBody` i un DTO, `404` si no existeix, `409` si xoca amb un altre recurs, i `200` amb el recurs modificat.

### Casos de prova

Amb les dades dels CSV (estudis CD Projekt Red, Supergiant Games i Nintendo; plataformes PC, PS5 i Switch):

| Petició | Cos | Resposta |
|---|---|---|
| `POST /api/plataformes?nom=Xbox` | | `201` · `PLATAFORMA INSERIDA: Plataforma [id=..., nom=Xbox]` |
| `POST /api/plataformes?nom=PC` | | `409` · `ERROR: JA EXISTEIX LA PLATAFORMA PC` |
| `GET /api/plataformes/99` | | `404` · `NO EXISTEIX CAP PLATAFORMA AMB ID 99` |
| `DELETE /api/plataformes/{id de PC}` | | `409` · té llançaments |
| `DELETE /api/plataformes/{id de Xbox}` | | `204` |
| `POST /api/videojocs` | el JSON de [§7](#dto) | `201`, `Location: /api/videojocs/{id nou}` i el `VideojocResposta` |
| `POST /api/videojocs` | el mateix JSON | `409` · `ERROR: JA EXISTEIX EL VIDEOJOC Hades II` |
| `POST /api/videojocs` | `{"nom": "", "estudi": "Nintendo", "categories": [], "preus": {}}` | `400` · `{"nom":"El nom és obligatori"}` |
| `POST /api/videojocs` | `{"nom": "Hades II"` (falta `}`) | `400` (JSON mal format) |
| `POST /api/videojocs` | el JSON de [§7](#dto) amb `"estudi": "Valve"` | `404` · `NO EXISTEIX L'ESTUDI Valve` |
| `GET /api/videojocs/99` | | `404` · `NO EXISTEIX CAP VIDEOJOC AMB ID 99` |

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
| Document de proves | Col·lecció de Postman o captures de Swagger |

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
| Al cos de l'error surt `null` | `ResponseEntity<Plataforma>` amb `.body(null)` en el cas d'error | `ResponseEntity<?>` i el missatge al cos ([§6](#retornar)) |
| Caràcters estranys a `Header-Customitzat` | Accents a la capçalera | Sense accents; el missatge al cos ([§6](#retornar)) |
| `HttpMessageNotWritableException` o JSON infinit | Es retorna una entitat amb relacions bidireccionals | DTO ([§7](#dto)). Sessió 3 |
| `HttpMessageNotWritableException: No serializer found for class ...` | Es retorna un objecte sense getters públics | Afegir getters, o fer servir un `record` |
| Funciona amb Postman però no des d'una web | CORS | `@CrossOrigin` al controlador |
| `405 Method Not Allowed` | Verb equivocat (`GET` a un `@PostMapping`) | [§3](#metodes) |

---
