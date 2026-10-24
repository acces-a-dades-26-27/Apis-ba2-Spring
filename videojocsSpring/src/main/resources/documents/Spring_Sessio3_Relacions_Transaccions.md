# Spring Boot · Sessió 3: relacions, JSON i transaccions

**M0486 Accés a dades · M0490 BA2 Desenvolupament i consum d'API · 2n DAMVi**

---

## Índex

1. [On érem](#on-erem)
2. [`@Transactional`](#transactional)
   - [2.1 Què fa](#tx-que-fa)
   - [2.2 On es posa](#tx-on)
   - [2.3 `readOnly`](#tx-readonly)
   - [2.4 Quan fa rollback](#tx-rollback)
   - [2.5 Propagació: un mètode transaccional en crida un altre](#tx-propagacio)
   - [2.6 La trampa de l'autocrida](#tx-autocrida)
   - [2.7 *Dirty checking*: sense `save`](#tx-dirty)
   - [2.8 I sense `@Transactional`?](#tx-sense)
3. [LAZY, EAGER i *Open Session in View*](#lazy)
4. [Entitats amb relacions i JSON](#json)
   - [4.1 Els tres problemes](#json-problemes)
   - [4.2 `@JsonIgnore`: quan n'hi ha prou](#json-ignore)
   - [4.3 `@JsonManagedReference` i `@JsonBackReference`](#json-managed)
   - [4.4 DTO de resposta](#json-dto)
   - [4.5 Quina triar](#json-triar)
5. [El servei fort: `VideojocService`](#servei)
   - [5.1 Repositoris](#sv-repos)
   - [5.2 Estructura del servei](#sv-dto)
   - [5.3 El llistat: un DTO diferent](#sv-estructura)
   - [5.4 Modificar relacions: categories i llançaments](#sv-relacions)
   - [5.5 Una operació de negoci: `abaixaPreuPerCategoria`](#sv-abaixa)
   - [5.6 Diverses operacions, una transacció: `publicar`](#sv-publicar)
   - [5.7 Del DAO al servei, mètode a mètode](#sv-taula)
6. [El controlador de videojocs](#controlador)
7. [Càrrega inicial des de CSV](#csv)
8. [Cap a Catan](#catan)
9. [Errors típics](#errors)

---

<a id="on-erem"></a>
## 1. On érem

El model de la sessió de relacions d'Hibernate, amb el `fetch` que vam decidir amb criteri:

```
                     ┌──────────────┐
                     │  Categoria   │◄──┐ pare (N:1, EAGER)
                     │  id, nom     │───┘ filles (1:N, LAZY)       RECURSIVA
                     └──────▲───────┘
                            │ N:M pura (videojoc_categoria), EAGER
┌──────────┐  1:N    ┌──────┴───────┐  1:1   ┌──────────────┐
│  Estudi  │────────►│   Videojoc   │───────►│ FitxaTecnica │
│ id, nom  │ (LAZY)  │   id, nom    │        │ id, motor    │
└──────────┘         └──────┬───────┘        └──────────────┘
          videojoc.estudi   │ 1:N (LAZY)
          (N:1, EAGER)┌─────▼────────┐  N:1 (EAGER) ┌──────────────┐
                      │  Llancament  │─────────────►│  Plataforma  │
                      │  id, preu    │              │   id, nom    │
                      └──────────────┘              └──────────────┘
```

I el DAO fort, on cada mètode públic era una operació completa i una transacció:

```java
public String afegirCategoria(String nomVideojoc, String nomCategoria) {
    Session session = SessionManager.getSessionFactory().getCurrentSession();
    Transaction tx = session.beginTransaction();
    try {
        String resultat = afegirCategoria(session, nomVideojoc, nomCategoria);  // el privat fa la feina
        tx.commit();
        return resultat;
    } catch (RuntimeException e) {
        tx.rollback();
        throw e;
    }
}
```

A Spring, **el mateix** queda així:

```java
@Transactional
public ResponseEntity<?> afegirCategoria(int idVideojoc, String nomCategoria) {
	// només la feina: begin, commit i rollback els fa Spring
}
```

---

<a id="transactional"></a>
## 2. `@Transactional`

<a id="tx-que-fa"></a>
### 2.1 Què fa

Quan un bean té mètodes amb `@Transactional`, Spring no injecta l'objecte original sinó un **proxy**: un objecte que l'embolcalla i fa la feina de l'esquema del DAO.

```
 Controlador ──► [ PROXY de VideojocService ] ──────────────────────► VideojocService real
                   1. obre la transacció (i l'EntityManager = la "sessió")
                   2. crida el mètode real ────────────────────────► afegirCategoria(...)
                   3a. si acaba (amb qualsevol return) -> commit (flush + COMMIT)
                   3b. si llança RuntimeException      -> rollback, i l'excepció continua cap amunt
                   4. tanca l'EntityManager: les entitats passen a DETACHED
```

Dins del mètode, **tots** els repositoris que es facin servir comparteixen la mateixa transacció i el mateix context de persistència. És el que fèiem passant la `Session` als mètodes privats.

<a id="tx-on"></a>
### 2.2 On es posa

```java
import org.springframework.transaction.annotation.Transactional;   // la de Spring, NO jakarta.transaction.Transactional
```

| On | Sí / No |
|---|---|
| Mètodes **públics** dels **serveis** | Sí. És el lloc |
| A la classe del servei (`@Transactional` a dalt) | Sí: s'aplica a tots els mètodes públics. Un mètode pot portar-ne una altra per sobreescriure-la |
| Controladors | No. El controlador és HTTP; una transacció per petició amaga on comença i acaba la feina |
| Repositoris de Spring Data | No cal: els seus mètodes ja són transaccionals |
| Mètodes `private` | **No funciona**: el proxy no els veu ([§2.6](#tx-autocrida)) |

<a id="tx-readonly"></a>
### 2.3 `readOnly`

```java
@Transactional(readOnly = true)
public VideojocResposta mostrar(int id) { ... }
```

Per a mètodes que només llegeixen. Hibernate no comprova canvis en tancar (no fa *dirty checking*) i el driver pot optimitzar. A més, documenta la intenció: si algú hi afegeix una modificació, no es desarà.

<a id="tx-rollback"></a>
### 2.4 Quan fa rollback

Spring decideix entre commit i rollback **segons com acaba el mètode**:

| Què passa dins del mètode | Resultat |
|---|---|
| Acaba normalment (**també si retorna un `ResponseEntity` d'error**: `404`, `409`...) | **Commit** |
| Llança una `RuntimeException` | **Rollback** |
| Llança una excepció *checked* (`IOException`, `Exception`...) | **Commit!** Spring només fa rollback de les no comprovades |
| Captura l'excepció amb un `try/catch` i no la torna a llançar | **Commit**: Spring no sap que hi ha hagut un error |
| Crida `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` i acaba | **Rollback** |

La primera fila és la important per a nosaltres: els nostres serveis **retornen** els errors (no els llancen). Per a Spring, un `return` és un mètode que ha acabat bé. Per tant:

**1. Comprovar abans de modificar.** Si tots els `return` d'error són **abans** de tocar cap entitat, no hi ha res a desfer. És el cas normal: `afegirCategoria`, `afegirLlancament`, `modificar`, `esborrar`... ([§5.4](#sv-relacions)).

**2. Si l'error apareix després d'haver modificat**, cal marcar la transacció perquè faci rollback abans de retornar:

```java
import org.springframework.transaction.interceptor.TransactionAspectSupport;

repository.save(v);                                  // ja s'ha fet un INSERT
...
if (!c.isPresent()) {
	TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();   // en acabar: ROLLBACK
	return ResponseEntity.status(HttpStatus.NOT_FOUND).body("... NO S'HA DESAT RES");
}
```

És l'equivalent del `rollback()` explícit del DAO quan el resultat era un error. Ho fem servir a `publicar` ([§5.6](#sv-publicar)).

**3. Excepcions *checked*.** Per fer rollback també amb aquestes:

```java
@Transactional(rollbackFor = Exception.class)
public int carregarDades() throws IOException { ... }
```

> **Regla de classe:** a cada mètode `@Transactional` que modifica dades, preguntar-se: *hi ha algun `return` d'error després d'una modificació?* Si n'hi ha, `setRollbackOnly()` just abans.

<a id="tx-propagacio"></a>
### 2.5 Propagació: un mètode transaccional en crida un altre

Al DAO, un mètode públic no en podia cridar un altre de públic (`Transaction already active`), i per això els privats rebien la `Session`. A Spring no hi ha aquest problema:

| Propagació | Què fa si ja hi ha una transacció |
|---|---|
| `REQUIRED` (**per defecte**) | S'hi **afegeix**. Tot és una sola transacció: si una part falla, es desfà tot |
| `REQUIRES_NEW` | Suspèn la de fora i n'obre una de nova i independent (per exemple, per desar un registre d'errors que no s'ha de desfer) |

```java
@Service
public class PartidaService {

	@Autowired
	private JugadorService jugadorService;

	@Transactional
	public ResponseEntity<String> construirPoblat(int idJugador, int vertex) {
		...
		jugadorService.sumarPunts(idJugador, 1);   // també @Transactional: s'afegeix a aquesta
		comprovarVictoria(...);                    // mètode privat: mateixa transacció
		...
	}
}
```

<a id="tx-autocrida"></a>
### 2.6 La trampa de l'autocrida

`@Transactional` funciona perquè la crida **passa pel proxy**. Una crida des de dins de la mateixa classe (`this.metode()`) **no hi passa**:

```java
@Service
public class VideojocService {

	// SENSE @Transactional
	public void ferMoltesCoses() {
		publicar(peticio);        // = this.publicar(...): NO passa pel proxy -> SENSE transacció
	}

	@Transactional
	public ResponseEntity<?> publicar(VideojocPeticio peticio) { ... }
}
```

| Cas | Hi ha transacció? |
|---|---|
| El controlador crida `service.publicar(...)` | Sí |
| `ferMoltesCoses()` (sense `@Transactional`) crida `publicar(...)` de la mateixa classe | **No** |
| `ferMoltesCoses()` és `@Transactional` i crida `publicar(...)` | Sí, la de `ferMoltesCoses` (que és la mateixa: `REQUIRED`) |

Solució: el mètode que el controlador crida directament és el que porta `@Transactional`. Els que crida per dins poden ser privats sense anotació, com al DAO.

<a id="tx-dirty"></a>
### 2.7 *Dirty checking*: sense `save`

Dins d'una transacció, les entitats que retorna un repositori són **gestionades** (*managed*). Si se'n modifica un atribut, Hibernate ho detecta i fa l'`UPDATE` en fer commit, **sense cridar `save`**:

```java
// EstudiService.modificar (sessió 2), simplificat
@Transactional
public ResponseEntity<?> modificar(int id, EstudiPeticio peticio) {
	Optional<Estudi> e = repository.findById(id);
	...
	e.get().setNom(peticio.nom());
	// cap save: UPDATE estudi SET nom=? WHERE id=? en sortir del mètode
	return ResponseEntity.ok().body(e.get());
}
```

És el mateix que a Hibernate. Cridar `save` igualment no fa mal (fa un `merge` d'una entitat que ja és gestionada, que no fa res), però cal entendre que no és necessari.

| Cal `save`? | |
|---|---|
| Entitat **nova** (`new Videojoc(...)`) | **Sí** (o que arribi per *cascade* des d'una de gestionada) |
| Entitat obtinguda d'un repositori **dins** de la mateixa transacció | No |
| Entitat obtinguda **fora** de transacció (o d'una transacció anterior) | Sí: és *detached* |

<a id="tx-sense"></a>
### 2.8 I sense `@Transactional`?

Cada crida a un repositori és **una transacció independent**:

```java
// SENSE @Transactional
public void abaixaPreus(...) {
    List<Llancament> ll = lRepository.findByCategories(...);  // transacció 1. En acabar: DETACHED
    for (Llancament l : ll) {
        l.setPreu(...);                                          // ningú no ho detecta
    }
    // cap UPDATE. Caldria lRepository.saveAll(ll): transacció 2
    // i si falla a mitges, la 1 ja està confirmada
}
```

Problemes: les entitats queden *detached* de seguida, les relacions LAZY peten en accedir-hi ([§3](#lazy)), i una operació que fa diverses escriptures pot quedar a mitges. Per això: **tota operació de negoci que toqui dades, `@Transactional`**.

---

<a id="lazy"></a>
## 3. LAZY, EAGER i *Open Session in View*

El criteri per triar EAGER o LAZY és **el de la sessió de relacions d'Hibernate**, sense canvis. El que canvia és **on és la sessió**:

| Hibernate amb DAO | Spring |
|---|---|
| La sessió viu dins del mètode públic del DAO | La sessió (l'`EntityManager`) viu dins del mètode `@Transactional` |
| Una entitat retornada pel DAO és *detached* | Una entitat retornada pel servei és *detached* |
| Accedir a una relació LAZY no carregada → `LazyInitializationException` | **Igual**... si `open-in-view` és `false` |

### *Open Session in View* (OSIV)

Spring Boot, **per defecte**, manté l'`EntityManager` obert durant **tota la petició HTTP**, també mentre el controlador construeix la resposta i Jackson genera el JSON. Avisa al log:

```
spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering.
```

| `open-in-view=true` (per defecte) | `open-in-view=false` (recomanat) |
|---|---|
| Accedir a una relació LAZY des del controlador o des de Jackson **funciona**: fa la `SELECT` en aquell moment | `LazyInitializationException` |
| Les `SELECT` surten d'on menys t'esperes: en convertir a JSON una llista de 50 videojocs, 50 `SELECT` de llançaments (problema N+1) | Totes les consultes són al servei, on es veuen |
| La connexió a la BD queda ocupada fins que s'ha enviat la resposta | La connexió s'allibera en acabar el servei |
| Amaga errors de disseny: el codi "funciona" i no se sap per què | Obliga a pensar què necessita cada resposta |

Els projectes de l'any passat funcionaven amb OSIV activat i moltes relacions EAGER. **Farem servir `false`**:

```properties
spring.jpa.open-in-view=false
```

Així es comporta igual que el DAO fort: la feina es fa dins del servei.

### `LazyInitializationException`: les solucions

Com a la sessió del DAO, **no es resol canviant la relació a EAGER**. Primer cal preguntar-se si la relació compleix el criteri. Si no, les solucions són:

| Solució | On | Quan |
|---|---|---|
| Fer la feina dins del servei `@Transactional` | Servei | Sempre que la feina sigui una operació (calcular, modificar) |
| Construir el **DTO** dins del servei | Servei | Quan el resultat va a l'API ([§4.4](#json-dto)) |
| `JOIN FETCH` a la consulta, o `@EntityGraph` | Repositori (sessió 4) | Quan cal retornar entitats amb les relacions ja carregades, o per evitar N+1 |

---

<a id="json"></a>
## 4. Entitats amb relacions i JSON

<a id="json-problemes"></a>
### 4.1 Els tres problemes

Jackson converteix un objecte en JSON cridant **tots els getters**. Amb entitats relacionades, això dona tres problemes diferents:

**1. Bucle infinit** en una relació bidireccional:

```
Estudi.getVideojocs() -> [Videojoc]
    Videojoc.getEstudi() -> Estudi
        Estudi.getVideojocs() -> [Videojoc]
            ...
```

Resultat: `StackOverflowError`, o un error de Jackson de profunditat màxima (*nesting depth*), i un `500`.

**2. `LazyInitializationException`** (amb `open-in-view=false`): Jackson crida `getLlancaments()` d'un videojoc *detached* i la col·lecció no està carregada.

**3. Proxys d'Hibernate**: una relació LAZY `@ManyToOne` no carregada és un *proxy* (una subclasse que genera Hibernate). Jackson n'intenta convertir els atributs interns i falla amb `No serializer found for class ...ByteBuddyInterceptor` (o surt un camp `hibernateLazyInitializer` al JSON).

Hi ha dues maneres de resoldre-ho, i al projecte fem servir **les dues**, cadascuna on toca:

| Opció | Idea | Al projecte |
|---|---|---|
| **`@JsonIgnore`** | Retornar l'entitat, però amagar les relacions que fan mal | `Plataforma`, `Estudi`, `Categoria` |
| **DTO de resposta** | Retornar un objecte fet a mida, construït dins del servei | `Videojoc`, `Llancament` |

<a id="json-ignore"></a>
### 4.2 `@JsonIgnore`: quan n'hi ha prou

`@JsonIgnore` treu un atribut del JSON. Es posa al **costat invers** de cada relació bidireccional, i a les referències de tornada:

```java
// Estudi: la llista de videojocs (LAZY, creix sense límit)
@OneToMany(mappedBy="estudi", cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.LAZY)
@JsonIgnore
private List<Videojoc> videojocs = new ArrayList<>();

// Categoria: les filles (LAZY). El pare (EAGER) sí que surt
@OneToMany(mappedBy="pare", cascade=CascadeType.ALL, orphanRemoval=true)
@OrderBy("nom")
@JsonIgnore
private List<Categoria> filles = new ArrayList<>();

// FitxaTecnica i Llancament: la referència de tornada al videojoc
@JsonIgnore
private Videojoc videojoc;
```

Amb això, `Estudi` i `Categoria` es poden retornar directament:

```json
GET /api/categories
[
  { "id": 5, "nom": "Acció", "pare": null },
  { "id": 6, "nom": "JRPG", "pare": { "id": 2, "nom": "RPG", "pare": null } },
  { "id": 3, "nom": "Roguelike", "pare": { "id": 5, "nom": "Acció", "pare": null } },
  ...
]
```

**Quan n'hi ha prou amb `@JsonIgnore`** (totes tres condicions):
1. Les relacions que **surten** al JSON són EAGER (ja estan carregades quan l'entitat surt del servei).
2. Les que **no** surten porten `@JsonIgnore`.
3. El JSON que voleu té la mateixa forma que l'entitat.

**`@JsonIgnoreProperties`** és més fi: en un atribut concret, amaga alguns camps de l'objecte relacionat.

```java
// Mostra l'estudi del videojoc, però sense la seva llista de videojocs
@ManyToOne(fetch=FetchType.EAGER, optional=false)
@JoinColumn(name="estudi_id", nullable=false)
@JsonIgnoreProperties("videojocs")
private Estudi estudi;
```

Les anotacions de Jackson són a `com.fasterxml.jackson.annotation` (també a Jackson 3).

**Quan no n'hi ha prou: `Videojoc`.** Té `llancaments` LAZY. Si li traiem el `@JsonIgnore`, peta fora del servei; si l'hi posem, no surten els preus, que és justament el que volem mostrar. Cap anotació ho resol: cal un DTO.

<a id="json-managed"></a>
### 4.3 `@JsonManagedReference` i `@JsonBackReference`

La parella que fèiem servir l'any passat:

```java
// Estudi (pare): aquest costat SÍ surt
@OneToMany(mappedBy="estudi")
@JsonManagedReference
private List<Videojoc> videojocs;

// Videojoc (fill): aquest costat NO surt
@ManyToOne
@JsonBackReference
private Estudi estudi;
```

Funciona per a relacions pare-fill (1:N), però decideix **una sola direcció per a tota l'aplicació**: si a `GET /api/videojocs/3` voleu veure de quin estudi és, ja no podeu. En N:M és fàcil equivocar-se de costat, i en relacions recursives no serveix. `@JsonIgnore` / `@JsonIgnoreProperties` fan el mateix amb menys sorpreses.

<a id="json-dto"></a>
### 4.4 DTO de resposta

Un DTO de resposta és un objecte fet **per a una resposta concreta**: té exactament els camps que ha de veure el client, amb la forma que li va bé, i només dades simples (text, números, llistes d'altres DTO).

```java
/*
 * DTO de RESPOSTA amb el DETALL d'un videojoc.
 * Resol els tres problemes de retornar l'entitat:
 *   - no hi ha relacions bidireccionals -> no hi ha bucle infinit
 *   - es construeix DINS del servei     -> els llançaments (LAZY) es poden llegir
 *   - només hi ha String i números      -> cap proxy d'Hibernate arriba a Jackson
 */
public record VideojocResposta(Integer id, String nom, String estudi, String motor,
		List<String> categories, List<PreuResposta> preus) {

	// COMPTE: s'ha de cridar DINS d'un mètode @Transactional (llegeix videojoc.llancaments, que és LAZY)
	public static VideojocResposta de(Videojoc v) {
		String motor = null;
		if (v.getFitxa() != null) {
			motor = v.getFitxa().getMotor();
		}

		// Categories amb la seva ruta a l'arbre: "Acció > Roguelike"
		ArrayList<String> categories = new ArrayList<>();
		for (Categoria c : v.getCategories()) {
			categories.add(ruta(c));
		}
		Collections.sort(categories);

		ArrayList<PreuResposta> preus = new ArrayList<>();
		for (Llancament l : v.getLlancaments()) {
			preus.add(PreuResposta.de(l));
		}

		return new VideojocResposta(v.getId(), v.getNom(), v.getEstudi().getNom(), motor, categories, preus);
	}

	// Puja per l'arbre fins a l'arrel (pare és EAGER i la cadena és curta)
	private static String ruta(Categoria c) {
		if (c.getPare() == null) {
			return c.getNom();
		}
		return ruta(c.getPare()) + " > " + c.getNom();
	}

}
```

```java
public record PreuResposta(String plataforma, BigDecimal preu) {

	public static PreuResposta de(Llancament l) {
		return new PreuResposta(l.getPlataforma().getNom(), l.getPreu());
	}

}
```

```json
{
  "id": 1,
  "nom": "The Witcher 3",
  "estudi": "CD Projekt Red",
  "motor": "REDengine 3",
  "categories": ["RPG"],
  "preus": [
    { "plataforma": "PC", "preu": 29.99 },
    { "plataforma": "PS5", "preu": 39.99 },
    { "plataforma": "Switch", "preu": 34.99 }
  ]
}
```

El JSON té la forma que necessita qui el consumeix (`"estudi": "CD Projekt Red"`, `"motor"` directament al videojoc), no la forma de les taules.

#### Les regles dels DTO de resposta

| Regla | Per què | Al projecte |
|---|---|---|
| **Un `record` per resposta**, amb el nom acabat en `Resposta` o `Resum` | Es veu d'un cop d'ull què és entrada (`...Peticio`) i què és sortida | `VideojocResposta`, `VideojocResum`, `LlancamentResposta` |
| **La conversió, en un sol lloc**: un mètode `static de(Entitat)` dins del record | Si canvia l'entitat, només es toca aquest mètode | `VideojocResposta.de(v)` |
| **Es construeix dins d'un mètode `@Transactional`** del servei | Les relacions LAZY només es poden llegir amb la transacció oberta | `VideojocService.mostrar` |
| **Només dades simples o altres DTO**, mai una entitat dins d'un DTO | Una entitat dins del DTO torna a portar tots els problemes | `List<PreuResposta>`, no `List<Llancament>` |
| **Un DTO per necessitat**, no un per entitat | Un llistat no necessita el mateix que un detall | `VideojocResum` (llistat) i `VideojocResposta` (detall) |
| **Les relacions, aplanades** quan només en cal un camp | `"estudi": "Nintendo"` és més útil que `"estudi": {"id": 3, "nom": "Nintendo"}` | `estudi`, `plataforma` |

#### Un recurs, diversos DTO

| DTO | Camps | Endpoint |
|---|---|---|
| `VideojocResum` | `id`, `nom`, `estudi` | `GET /api/videojocs` (llistat: molts jocs, poca informació) |
| `VideojocResposta` | `id`, `nom`, `estudi`, `motor`, `categories`, `preus` | `GET /api/videojocs/{id}` (detall) i les modificacions |
| `LlancamentResposta` | `videojoc`, `plataforma`, `preu` | Consultes de llançaments (sessió 4): un preu vist des de fora del joc |
| `PaginaResposta<T>` | `contingut`, `pagina`, `mida`, `total`, `totalPagines` | Llistats paginats (sessió 4) |

```java
public record VideojocResum(Integer id, String nom, String estudi) {

	public static VideojocResum de(Videojoc v) {
		return new VideojocResum(v.getId(), v.getNom(), v.getEstudi().getNom());
	}

}
```

#### DTO de petició amb relacions

El DTO de petició també té la forma que va bé al client, no la de les taules. Per publicar un videojoc, l'estudi, les categories i les plataformes arriben **pel nom**, i és el servei qui busca les entitats:

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

```json
{
  "nom": "Hades II",
  "estudi": "Supergiant Games",
  "motor": "Motor propi",
  "categories": ["Roguelike"],
  "preus": { "PC": 29.99, "Switch": 29.99 }
}
```

Fixeu-vos en `Map<String, @Positive BigDecimal>`: la validació s'aplica a **cada valor** del mapa.

<a id="json-triar"></a>
### 4.5 Quina triar

| | `@JsonIgnore` / `@JsonIgnoreProperties` | `@JsonManagedReference` / `@JsonBackReference` | DTO |
|---|---|---|---|
| Bucle infinit | Resolt | Resolt | Resolt |
| LAZY fora de transacció | **No** resolt | **No** resolt | Resolt (es construeix al servei) |
| Proxys d'Hibernate | No resolt | No resolt | Resolt |
| JSON diferent per a cada endpoint | No: és a l'entitat, val per a tot | No | Sí |
| Classes de més | Cap | Cap | Una per resposta |

**Criteri de classe:**

| Si l'entitat... | Feu servir | Al projecte |
|---|---|---|
| No té relacions, o només EAGER cap a entitats simples, i el JSON és com l'entitat | L'entitat amb `@JsonIgnore` als costats inversos | `Plataforma`, `Estudi`, `Categoria` |
| Té relacions LAZY que la resposta necessita | DTO construït al servei | `Videojoc` |
| La resposta ha de tenir una forma diferent de l'entitat (aplanada, amb camps calculats, amb dades d'altres entitats) | DTO | `Llancament` → `LlancamentResposta` |
| Rebre dades del client | **Sempre** DTO de petició (sessió 2) | `EstudiPeticio`, `VideojocPeticio` |

Encara que feu DTO, el `@JsonIgnore` als costats inversos és una bona **xarxa de seguretat**: si algú retorna l'entitat per error, no hi haurà bucle.

---

<a id="servei"></a>
## 5. El servei fort: `VideojocService`

<a id="sv-repos"></a>
### 5.1 Repositoris

Prims: només el que el servei necessita consultar. Les `@Query` s'expliquen a la sessió 4.

```java
public interface VideojocRepository extends JpaRepository<Videojoc, Integer> {
	Optional<Videojoc> findByNom(String nom);
	boolean existsByNom(String nom);
	boolean existsByEstudiId(Integer idEstudi);
	long countByEstudiId(Integer idEstudi);
}

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {
	Optional<Categoria> findByNom(String nom);
}

public interface LlancamentRepository extends JpaRepository<Llancament, Integer> {

	boolean existsByVideojocIdAndPlataformaId(Integer idVideojoc, Integer idPlataforma);

	// Llançaments dels jocs que tenen ALGUNA de les categories.
	// DISTINCT: un joc pot ser a dues categories del conjunt i no el volem abaixar dues vegades
	@Query("""
			SELECT DISTINCT l FROM Llancament l
			JOIN l.videojoc v
			JOIN v.categories c
			WHERE c IN :categories
			""")
	List<Llancament> findByCategories(@Param("categories") Collection<Categoria> categories);
}
```

I els d'`Estudi` i `Plataforma` de les sessions anteriors. Cinc repositoris i **un** servei fort: els repositoris són l'equivalent del `GenericDao`; la lògica és al servei.

<a id="sv-dto"></a>
### 5.2 Estructura del servei

```java
/*
 * El SERVEI FORT: l'equivalent del VideojocDao fort d'Hibernate.
 * Cada mètode públic és UNA operació completa i UNA transacció.
 * SORTIDA: DTO, MAI l'entitat Videojoc. Els DTO es construeixen DINS dels mètodes @Transactional.
 */
@Service
public class VideojocService {

	@Autowired
	private VideojocRepository repository;

	@Autowired
	private EstudiRepository eRepository;

	@Autowired
	private CategoriaRepository cRepository;

	@Autowired
	private PlataformaRepository pRepository;

	@Autowired
	private LlancamentRepository lRepository;

	// readOnly: només llegim; Hibernate no comprova canvis en tancar
	@Transactional(readOnly = true)
	public ResponseEntity<?> mostrar(int id) {
		Optional<Videojoc> v = repository.findById(id);
		if (!v.isPresent()) {
			return noTrobat(id);
		}
		// El DTO es construeix AQUÍ, amb la transacció oberta: els llançaments LAZY es poden llegir
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Dades videojoc ID: " + id)
				.body(VideojocResposta.de(v.get()));
	}

	// ... la resta d'operacions

	// Auxiliar privat: el 404 es repeteix a molts mètodes
	private ResponseEntity<?> noTrobat(int id) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.header("Header-Customitzat", "ID " + id + " NO TROBAT")
				.body("NO EXISTEIX CAP VIDEOJOC AMB ID " + id);
	}

}
```

> Al projecte, `mostrar` fa servir `findWithLlancamentsById` en lloc de `findById`, que porta els llançaments a la mateixa consulta. És una optimització de la sessió 4; el resultat és el mateix.

L'esquema del DAO (públic amb transacció + privats que fan part de la feina) es manté, però els privats **no reben la sessió**: la transacció la porta Spring.

<a id="sv-estructura"></a>
### 5.3 El llistat: un DTO diferent

```java
@Transactional(readOnly = true)
public ResponseEntity<List<VideojocResum>> llistar(String categoria) {
	List<Videojoc> videojocs;
	if (categoria == null) {
		videojocs = repository.findAll(Sort.by("nom"));
	} else {
		videojocs = repository.llistarPerCategoria(categoria);   // @Query amb JOIN FETCH (sessió 4)
	}

	ArrayList<VideojocResum> resultat = new ArrayList<>();
	for (Videojoc v : videojocs) {
		resultat.add(VideojocResum.de(v));
	}
	return ResponseEntity.ok()
			.header("Header-Customitzat", "Total videojocs: " + resultat.size())
			.body(resultat);
}
```

> Si en lloc de `VideojocResum` féssim `VideojocResposta` per a cada joc, hi hauria una `SELECT` de llançaments per a cada videojoc: el problema **N+1**. Al projecte, `GET /api/videojocs/complet` ho fa bé amb `@EntityGraph` (sessió 4). Activeu `show-sql` i compteu les consultes.

<a id="sv-relacions"></a>
### 5.4 Modificar relacions: categories i llançaments

**N:M pura** (`videojoc_categoria`): només cal afegir a la col·lecció del costat propietari.

```java
@Transactional
public ResponseEntity<?> afegirCategoria(int idVideojoc, String nomCategoria) {
	Optional<Videojoc> v = repository.findById(idVideojoc);
	Optional<Categoria> c = cRepository.findByNom(nomCategoria);
	if (!v.isPresent()) {
		return noTrobat(idVideojoc);
	}
	if (!c.isPresent()) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.header("Header-Customitzat", "CATEGORIA NO TROBADA")
				.body("NO EXISTEIX LA CATEGORIA " + nomCategoria);
	}
	// Comprovem ABANS de modificar
	if (v.get().getCategories().contains(c.get())) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.header("Header-Customitzat", "CATEGORIA REPETIDA")
				.body("ERROR: " + v.get().getNom() + " JA ÉS DE LA CATEGORIA " + nomCategoria);
	}
	v.get().afegirCategoria(c.get());
	// en sortir: commit -> INSERT INTO videojoc_categoria. Cap save
	return ResponseEntity.ok()
			.header("Header-Customitzat", "Categoria afegida al videojoc " + idVideojoc)
			.body(VideojocResposta.de(v.get()));
}
```

**N:M amb atributs** (`Llancament`): una entitat nova, que es desa per *cascade* des del videojoc.

```java
@Transactional
public ResponseEntity<?> afegirLlancament(int idVideojoc, String nomPlataforma, BigDecimal preu) {
	Optional<Videojoc> v = repository.findById(idVideojoc);
	Optional<Plataforma> p = pRepository.findByNom(nomPlataforma);
	if (!v.isPresent()) {
		return noTrobat(idVideojoc);
	}
	if (!p.isPresent()) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.header("Header-Customitzat", "PLATAFORMA NO TROBADA")
				.body("NO EXISTEIX LA PLATAFORMA " + nomPlataforma);
	}
	if (lRepository.existsByVideojocIdAndPlataformaId(idVideojoc, p.get().getId())) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.header("Header-Customitzat", "LLANCAMENT REPETIT")
				.body("ERROR: " + v.get().getNom() + " JA TÉ PREU A " + nomPlataforma);
	}
	v.get().afegirLlancament(p.get(), preu);       // new Llancament(...) a la llista
	// en sortir: INSERT INTO llancament (cascade = ALL de Videojoc.llancaments)
	return ResponseEntity.status(HttpStatus.CREATED)
			.header("Header-Customitzat", "Llancament afegit al videojoc " + idVideojoc)
			.body(VideojocResposta.de(v.get()));
}
```

Comprovar **abans** de modificar (el duplicat, que existeixin les dues entitats), com al DAO. Si no, l'`UNIQUE(videojoc_id, plataforma_id)` petaria al commit amb un error de BD poc clar. I com que no s'ha modificat res abans dels `return` d'error, no cal cap rollback ([§2.4](#tx-rollback)).

<a id="sv-abaixa"></a>
### 5.5 Una operació de negoci: `abaixaPreuPerCategoria`

Abaixa el preu de tots els llançaments dels videojocs d'una categoria **i de totes les seves subcategories**.

```java
@Transactional
public ResponseEntity<String> abaixaPreuPerCategoria(String nomCategoria, BigDecimal descompte) {
	Optional<Categoria> arrel = cRepository.findByNom(nomCategoria);
	if (!arrel.isPresent()) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.header("Header-Customitzat", "CATEGORIA NO TROBADA")
				.body("NO EXISTEIX LA CATEGORIA " + nomCategoria);
	}

	Set<Categoria> subarbre = new HashSet<>();
	recollirSubarbre(arrel.get(), subarbre);      // filles és LAZY: dins de la transacció, cap problema

	List<Llancament> llancaments = lRepository.findByCategories(subarbre);
	for (Llancament l : llancaments) {
		BigDecimal nou = l.getPreu().subtract(descompte);
		l.setPreu(nou.max(BigDecimal.ZERO));       // cap preu negatiu
	}
	// En sortir: commit -> un UPDATE per cada llançament modificat (dirty checking, sense saveAll)
	return ResponseEntity.ok()
			.header("Header-Customitzat", "Descompte aplicat per categoria")
			.body("S'HAN ABAIXAT " + llancaments.size() + " PREUS DE LA CATEGORIA " + nomCategoria
					+ " I LES SEVES SUBCATEGORIES");
}

private void recollirSubarbre(Categoria c, Set<Categoria> resultat) {
	resultat.add(c);
	for (Categoria filla : c.getFilles()) {
		recollirSubarbre(filla, resultat);
	}
}
```

Tot passa en una transacció: si l'`UPDATE` d'un llançament fallés, no se'n modificaria cap.

<a id="sv-publicar"></a>
### 5.6 Diverses operacions, una transacció: `publicar`

Crea un videojoc complet (fitxa, categories i preus) a partir del `VideojocPeticio` de [§4.4](#json-dto). És el cas on el rollback importa: si una categoria no existeix, **el videojoc ja s'ha desat**.

```java
@Transactional
public ResponseEntity<?> publicar(VideojocPeticio peticio) {
	// 1. Comprovacions abans de tocar res
	if (repository.existsByNom(peticio.nom())) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.header("Header-Customitzat", "VIDEOJOC DUPLICAT")
				.body("ERROR: JA EXISTEIX EL VIDEOJOC " + peticio.nom());
	}
	Optional<Estudi> estudi = eRepository.findByNom(peticio.estudi());
	if (!estudi.isPresent()) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.header("Header-Customitzat", "ESTUDI NO TROBAT")
				.body("NO EXISTEIX L'ESTUDI " + peticio.estudi());
	}

	Videojoc v = new Videojoc(peticio.nom());
	estudi.get().afegirVideojoc(v);
	if (peticio.motor() != null && !peticio.motor().isBlank()) {
		v.setFitxa(new FitxaTecnica(peticio.motor()));
	}
	repository.save(v);             // amb IDENTITY, l'INSERT es fa ARA (mireu el log)

	// 2. Errors que trobem DESPRÉS d'haver desat el videojoc
	for (String nomCat : peticio.categories()) {
		Optional<Categoria> c = cRepository.findByNom(nomCat);
		if (!c.isPresent()) {
			// Sense aquesta línia, el videojoc quedaria desat sense les seves categories
			TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.header("Header-Customitzat", "CATEGORIA NO TROBADA")
					.body("NO EXISTEIX LA CATEGORIA " + nomCat + ". NO S'HA DESAT RES");
		}
		v.afegirCategoria(c.get());
	}

	for (Map.Entry<String, BigDecimal> preu : peticio.preus().entrySet()) {
		Optional<Plataforma> p = pRepository.findByNom(preu.getKey());
		if (!p.isPresent()) {
			TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.header("Header-Customitzat", "PLATAFORMA NO TROBADA")
					.body("NO EXISTEIX LA PLATAFORMA " + preu.getKey() + ". NO S'HA DESAT RES");
		}
		v.afegirLlancament(p.get(), preu.getValue());
	}

	// 201 Created + Location
	return ResponseEntity.created(URI.create("/api/videojocs/" + v.getId()))
			.header("Header-Customitzat", "Videojoc publicat amb ID: " + v.getId())
			.body(VideojocResposta.de(v));
}
```

**Proveu-ho amb una categoria que no existeix** (`"categories": ["Shooter", "Terror"]`):
1. L'`INSERT` del videojoc ja s'ha fet (es veu al log).
2. `findByNom("Terror")` retorna un `Optional` buit.
3. `setRollbackOnly()` marca la transacció i el mètode retorna el `404`.
4. En sortir del mètode, Spring fa **rollback** en lloc de commit: l'`INSERT` es desfà.
5. `GET /api/videojocs` no mostra el joc nou.

**Proveu-ho també traient la línia `setRollbackOnly()`**: el client rep el mateix `404`, però el videojoc queda desat, amb les categories que hi havia abans de l'error i sense preus. És el "mig desat" de la sessió del DAO.

<a id="sv-taula"></a>
### 5.7 Del DAO al servei, mètode a mètode

| `VideojocDao` (Hibernate) | `VideojocService` (Spring) | Canvi |
|---|---|---|
| `carregarDades()` | `CarregaCsvService.carregarDades()` ([§7](#csv)) | `session.persist` → `repository.save` |
| `getNomFromID(id)` | `nom(id)` amb `repository.findNomById(id)` (sessió 4, projecció) | La consulta, al repositori |
| `mostrarVideojoc(nom)` → `String` | `mostrar(id)` → `ResponseEntity` amb `VideojocResposta` | Retorna un DTO; el JSON el fa Jackson |
| `llistarPerCategoria(nom)` amb `JOIN FETCH` | `llistar(categoria)` amb `repository.llistarPerCategoria` (sessió 4) | `@Query` al repositori |
| `afegirCategoria(...)` → missatge | `afegirCategoria(...)` → `ResponseEntity` amb el DTO o el missatge d'error | El missatge d'error porta el seu codi HTTP |
| `afegirLlancament(...)` | `afegirLlancament(...)` | |
| `abaixaPreuPerCategoria(...)` → `int` o `-1` | `abaixaPreuPerCategoria(...)` → `200` amb el recompte o `404` | |
| `publicarVideojoc(...)` → id o `-1`, rollback explícit | `publicar(VideojocPeticio)` → `201` o error, `setRollbackOnly()` | |
| `esborrarVideojoc(nom)` | `esborrar(id)` | |
| Mètodes privats `xxx(Session session, ...)` | Mètodes privats `xxx(...)` | No cal passar la sessió |
| `try { ... commit } catch (RuntimeException e) { rollback; throw e; }` | `@Transactional` | Spring |

---

<a id="controlador"></a>
## 6. El controlador de videojocs

Curt, com tots: cada mètode llegeix els paràmetres i crida el servei.

```java
/*
 * Videojocs. Totes les respostes són DTO:
 *   llistats -> VideojocResum      detall -> VideojocResposta
 */
@RestController
@RequestMapping(path="/api/videojocs")
public class VideojocController {

	@Autowired
	private VideojocService service;

	/*
	 * GET localhost:8080/api/videojocs
	 * GET localhost:8080/api/videojocs?categoria=RPG
	 * required=false: si no hi és, arriba null i el servei els retorna tots
	 */
	@GetMapping
	public ResponseEntity<List<VideojocResum>> llistar(@RequestParam(required = false) String categoria) {
		return service.llistar(categoria);
	}

	// GET localhost:8080/api/videojocs/1
	@GetMapping(path="/{id}")
	public ResponseEntity<?> mostrar(@PathVariable int id) {
		return service.mostrar(id);
	}

	// POST localhost:8080/api/videojocs   (el JSON de VideojocPeticio)
	@PostMapping
	public ResponseEntity<?> publicar(@Valid @RequestBody VideojocPeticio peticio) {
		return service.publicar(peticio);
	}

	// POST localhost:8080/api/videojocs/3/categories/RPG
	@PostMapping(path="/{id}/categories/{categoria}")
	public ResponseEntity<?> afegirCategoria(@PathVariable int id, @PathVariable String categoria) {
		return service.afegirCategoria(id, categoria);
	}

	// DELETE localhost:8080/api/videojocs/3/categories/RPG
	@DeleteMapping(path="/{id}/categories/{categoria}")
	public ResponseEntity<?> treureCategoria(@PathVariable int id, @PathVariable String categoria) {
		return service.treureCategoria(id, categoria);
	}

	// POST localhost:8080/api/videojocs/3/llancaments?plataforma=PS5&preu=24.99
	@PostMapping(path="/{id}/llancaments")
	public ResponseEntity<?> afegirLlancament(
			@PathVariable int id,
			@RequestParam String plataforma,
			@RequestParam @Positive(message = "El preu ha de ser positiu") BigDecimal preu) {
		return service.afegirLlancament(id, plataforma, preu);
	}

	// DELETE localhost:8080/api/videojocs/3
	@DeleteMapping(path="/{id}")
	public ResponseEntity<String> esborrar(@PathVariable int id) {
		return service.esborrar(id);
	}

}
```

I l'acció sobre les categories, al seu propi controlador:

```java
@RestController
@RequestMapping(path="/api/categories")
public class CategoriaController {

	@Autowired
	private CategoriaService service;

	// Un controlador pot fer servir qualsevol servei: el descompte és una operació del servei fort de videojocs
	@Autowired
	private VideojocService vService;

	// GET localhost:8080/api/categories -> entitats amb @JsonIgnore a les filles
	@GetMapping
	public ResponseEntity<List<Categoria>> llistar() {
		return service.llistar();
	}

	/*
	 * POST localhost:8080/api/categories/Acció/descompte?import=5
	 * "import" és una paraula reservada de Java: el paràmetre de l'URL es diu import i el de Java, descompte
	 */
	@PostMapping(path="/{nom}/descompte")
	public ResponseEntity<String> descompte(
			@PathVariable String nom,
			@RequestParam("import") @Positive(message = "El descompte ha de ser positiu") BigDecimal descompte) {
		return vService.abaixaPreuPerCategoria(nom, descompte);
	}

}
```

Un controlador pot fer servir qualsevol servei: el servei fort és de l'**agregat** (videojocs i tot el que hi penja), no d'una URL.

---

<a id="csv"></a>
## 7. Càrrega inicial des de CSV

Els **mateixos sis CSV** de la sessió de relacions, a `src/main/resources/dades/`:

| Fitxer | Capçalera |
|---|---|
| `estudis.csv` | `nom` |
| `plataformes.csv` | `nom` |
| `categories.csv` | `nom,pare` |
| `videojocs.csv` | `nom,estudi,motor` |
| `videojoc_categoria.csv` | `videojoc,categoria` |
| `llancaments.csv` | `videojoc,plataforma,preu` |

L'ordre de càrrega i les dues passades de les categories són les mateixes. Canvien dues coses: `session.persist(x)` passa a ser `repository.save(x)`, i la transacció la posa `@Transactional`.

### El servei de càrrega

```java
/*
 * És un servei (i no el CommandLineRunner directament) perquè es pugui cridar també des d'un
 * endpoint: a Catan, el tauler es carrega dels CSV quan es crea la partida (POST /partida).
 */
@Service
public class CarregaCsvService {

	@Autowired
	private EstudiRepository eRepository;

	@Autowired
	private PlataformaRepository pRepository;

	@Autowired
	private CategoriaRepository cRepository;

	@Autowired
	private VideojocRepository vRepository;

	/*
	 * UNA transacció: si un fitxer té un error, no es desa res.
	 * rollbackFor = Exception.class: IOException és CHECKED, i per defecte Spring
	 * només fa rollback amb RuntimeException. Sense això, faria commit del que s'hagués carregat.
	 */
	@Transactional(rollbackFor = Exception.class)
	public int carregarDades() throws IOException {
		// estudis.csv -> nom
		HashMap<String, Estudi> estudis = new HashMap<>();
		for (String[] fila : llegirCsv("estudis.csv")) {
			estudis.put(fila[0], eRepository.save(new Estudi(fila[0])));
		}

		// plataformes.csv -> nom
		HashMap<String, Plataforma> plataformes = new HashMap<>();
		for (String[] fila : llegirCsv("plataformes.csv")) {
			plataformes.put(fila[0], pRepository.save(new Plataforma(fila[0])));
		}

		/*
		 * categories.csv -> nom,pare (pare buit = arrel)
		 * DUES PASSADES perquè una filla pot aparèixer ABANS que el seu pare.
		 * Només desem les ARRELS: el cascade de 'filles' desa la resta de l'arbre.
		 */
		ArrayList<String[]> filesCategories = llegirCsv("categories.csv");
		HashMap<String, Categoria> categories = new HashMap<>();
		for (String[] fila : filesCategories) {
			categories.put(fila[0], new Categoria(fila[0]));
		}
		for (String[] fila : filesCategories) {
			if (!fila[1].isEmpty()) {
				Categoria pare = obtenir(categories, fila[1], "categories.csv");
				pare.afegirFilla(categories.get(fila[0]));
			}
		}
		for (Categoria c : categories.values()) {
			if (c.getPare() == null) {
				cRepository.save(c);
			}
		}

		// videojocs.csv -> nom,estudi,motor
		HashMap<String, Videojoc> videojocs = new HashMap<>();
		for (String[] fila : llegirCsv("videojocs.csv")) {
			Videojoc v = new Videojoc(fila[0]);
			obtenir(estudis, fila[1], "videojocs.csv").afegirVideojoc(v);
			if (!fila[2].isEmpty()) {
				v.setFitxa(new FitxaTecnica(fila[2]));
			}
			videojocs.put(fila[0], vRepository.save(v));
		}

		// videojoc_categoria.csv -> N:M PURA. Les dues entitats ja són gestionades: cap save
		for (String[] fila : llegirCsv("videojoc_categoria.csv")) {
			Videojoc v = obtenir(videojocs, fila[0], "videojoc_categoria.csv");
			v.afegirCategoria(obtenir(categories, fila[1], "videojoc_categoria.csv"));
		}

		// llancaments.csv -> N:M AMB ATRIBUTS. Llancament nou que es desa per cascade des del videojoc
		for (String[] fila : llegirCsv("llancaments.csv")) {
			Videojoc v = obtenir(videojocs, fila[0], "llancaments.csv");
			Plataforma p = obtenir(plataformes, fila[1], "llancaments.csv");
			v.afegirLlancament(p, new BigDecimal(fila[2]));
		}

		return videojocs.size();
	}

	// Busca una clau al HashMap. Si no hi és, el CSV fa referència a una cosa que no existeix
	private <T> T obtenir(HashMap<String, T> mapa, String clau, String fitxer) {
		T t = mapa.get(clau);
		if (t == null) {
			// RuntimeException -> rollback de tota la càrrega
			throw new IllegalStateException(fitxer + ": no existeix '" + clau + "'");
		}
		return t;
	}

	/*
	 * Llegeix un CSV del classpath (src/main/resources/dades/) i en retorna les files sense la capçalera.
	 * split(",", -1): el -1 conserva les columnes buides del final ("Acció," -> ["Acció", ""]).
	 */
	private ArrayList<String[]> llegirCsv(String fitxer) throws IOException {
		ClassPathResource recurs = new ClassPathResource("dades/" + fitxer);
		InputStreamReader isr = new InputStreamReader(recurs.getInputStream(), StandardCharsets.UTF_8);
		BufferedReader br = new BufferedReader(isr);

		ArrayList<String[]> files = new ArrayList<>();
		br.readLine();                          // capçalera
		while (br.ready()) {
			String linia = br.readLine();
			if (!linia.isBlank()) {
				files.add(linia.split(",", -1));
			}
		}
		br.close();

		return files;
	}

}
```

| Peça | Per què |
|---|---|
| `ClassPathResource("dades/...")` | Busca el fitxer dins de `src/main/resources`. Funciona des d'Eclipse i dins del `.jar`; un `FileReader("src/main/resources/...")` només funcionaria des d'Eclipse |
| `InputStreamReader(..., StandardCharsets.UTF_8)` | Els CSV són UTF-8 (`Acció`). Sense indicar-ho, depèn del sistema operatiu |
| `@Transactional(rollbackFor = Exception.class)` | `llegirCsv` llança `IOException`, que és *checked*: sense `rollbackFor`, no faria rollback ([§2.4](#tx-rollback)) |
| `throw new IllegalStateException(...)` a `obtenir` | Una clau del CSV que no existeix és un error de dades: `RuntimeException` → rollback de tot |

### Executar-la en arrencar: `CommandLineRunner`

Un bean que implementa `CommandLineRunner` s'executa **un cop**, quan l'aplicació ja ha arrencat (BD connectada, taules creades) i abans d'atendre peticions:

```java
@Component
public class CarregaInicial implements CommandLineRunner {

	@Autowired
	private CarregaCsvService carrega;

	@Autowired
	private VideojocRepository vRepository;

	@Override
	public void run(String... args) throws Exception {
		if (vRepository.count() > 0) {
			System.out.println("La BD ja té dades: no es carreguen els CSV");
			return;
		}
		int n = carrega.carregarDades();
		System.out.println("Videojocs carregats dels CSV: " + n);
	}

}
```

| `ddl-auto` | Què passa en cada arrencada |
|---|---|
| `create` | Taules noves i buides → sempre es carreguen els CSV. Cada arrencada comença igual: ideal per provar |
| `update` | Les dades es conserven → la comprovació `count() > 0` evita duplicar-les |

**Per què la càrrega és en un servei i no dins del `run`?** Perquè així es pot cridar també des d'un endpoint. A Catan, el tauler es carrega dels CSV **quan es crea una partida** (`POST /partida`), no en arrencar.

---

<a id="catan"></a>
## 8. Cap a Catan

| A Catan | Peça d'avui |
|---|---|
| La lògica de `PartidaDao` i `JugadorDao` passa a `PartidaService` i `JugadorService` | [§5](#servei) |
| Cada endpoint que modifica dades crida un mètode `@Transactional` | [§2](#transactional) |
| `ConstruirPoblat` crida `ComprovarVictoria` dins de la mateixa transacció | [§2.5](#tx-propagacio) |
| "No és el teu torn", "posició ocupada", "sense recursos"... | Un `ResponseEntity` amb el codi i el text de cada cas, comprovat **abans** de modificar; si no pot ser, `setRollbackOnly()` ([§2.4](#tx-rollback)) |
| `POST /partida` carrega el tauler dels CSV en una transacció | [§7](#csv): el servei de càrrega cridat des del servei de partida |
| `GET /partida/{id}/tauler`: hexàgons amb terreny, número, lladre i construccions | DTO construït al servei ([§4.4](#json-dto)) |
| `GET /jugador/{id}/recursos`: el jugador amb el seu inventari | Els recursos del jugador són una relació (probablement LAZY): DTO construït al servei ([§4.4](#json-dto), [§4.5](#json-triar)) |
| Herència `JOINED` de les construccions | Les entitats no canvien: el repositori de la classe pare retorna les filles |
| EAGER només amb justificació | [§3](#lazy) i la sessió de relacions |
| `open-in-view` | `false`: si alguna cosa peta per LAZY, la feina no és dins del servei |

---

<a id="errors"></a>
## 9. Errors típics

| Error | Causa | Solució |
|---|---|---|
| `LazyInitializationException: could not initialize proxy - no Session` | S'accedeix a una relació LAZY fora d'una transacció (al controlador, a Jackson) | DTO construït al servei, o `JOIN FETCH` ([§3](#lazy)). No canviar a EAGER sense criteri |
| JSON infinit, `StackOverflowError`, `nesting depth` | Entitat bidireccional retornada directament | DTO, o `@JsonIgnore` al costat invers ([§4](#json)) |
| `No serializer found for class ...ByteBuddyInterceptor` / camp `hibernateLazyInitializer` | Jackson intenta convertir un proxy LAZY | DTO ([§4.4](#json-dto)) |
| Una modificació no es desa | El mètode no és `@Transactional` i no es crida `save` ([§2.8](#tx-sense)), o és `readOnly` | `@Transactional` |
| Es desa mitja operació després d'un error | S'ha retornat un `ResponseEntity` d'error **després** d'haver modificat, o una excepció *checked* o capturada ([§2.4](#tx-rollback)) | Comprovar abans de modificar; `setRollbackOnly()`; `rollbackFor` |
| `NoTransactionException: No transaction aspect-managed TransactionStatus in scope` | `setRollbackOnly()` cridat en un mètode sense `@Transactional` | El mètode ha de ser `@Transactional` ([§2.4](#tx-rollback)) |
| `@Transactional` no fa res | El mètode és `private`, o es crida des de la mateixa classe ([§2.6](#tx-autocrida)) | Que el cridi el controlador (o un altre bean) |
| `@Transactional(readOnly = true)` no es reconeix | S'ha importat `jakarta.transaction.Transactional` | `org.springframework.transaction.annotation.Transactional` ([§2.2](#tx-on)) |
| `UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only` | Un altre bean `@Transactional` ha llançat una excepció dins la mateixa transacció i el mètode de fora l'ha capturada | No capturar-la, o que el de dins sigui `REQUIRES_NEW` si realment ha de ser independent |
| `DataIntegrityViolationException` al commit | S'ha violat un `UNIQUE` o una FK que no s'havia comprovat | Comprovar abans de modificar ([§5.4](#sv-relacions)) |
| Les dades dels CSV es dupliquen a cada arrencada | `ddl-auto=update` sense la comprovació `count() > 0` | [§7](#csv) |
| `FileNotFoundException: class path resource [dades/...]` | El CSV no és a `src/main/resources/dades/` o el nom no coincideix | Revisar la ruta i les majúscules |
| `IllegalStateException: videojocs.csv: no existeix 'Nintend'` | Una clau del CSV no coincideix amb cap fila del fitxer on apunta | Corregir el CSV |
| Avís `spring.jpa.open-in-view is enabled by default` | No s'ha configurat | `spring.jpa.open-in-view=false` ([§3](#lazy)) |

---
