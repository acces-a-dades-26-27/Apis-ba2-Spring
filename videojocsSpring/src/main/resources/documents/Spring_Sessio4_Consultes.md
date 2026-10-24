# Spring Boot · Sessió 4: consultes amb Spring Data JPA

**M0486 Accés a dades · M0490 BA2 Desenvolupament i consum d'API · 2n DAMVi**

Fins ara els repositoris tenien el CRUD heretat i algun `findByNom`. Avui veiem totes les maneres de consultar: des d'un nom de mètode que Spring converteix en SQL fins a JPQL, SQL natiu, projeccions, actualitzacions massives i paginació. Tot el que fèiem amb HQL dins del DAO té el seu lloc al repositori.

Les consultes marcades amb **(projecte)** són al projecte `videojocsSpring`, amb el seu endpoint i la seva petició a la col·lecció de Postman. La resta són exemples per als exercicis.

---

## Índex

1. [Les maneres de consultar](#maneres)
2. [Consultes derivades del nom del mètode](#derivades)
   - [2.1 Estructura del nom](#der-estructura)
   - [2.2 Paraules clau](#der-paraules)
   - [2.3 Navegar per relacions](#der-relacions)
   - [2.4 Tipus de retorn](#der-retorn)
   - [2.5 Limitar els resultats](#der-limitar)
   - [2.6 Quan deixar-les](#der-quan)
3. [`@Query` amb JPQL](#jpql)
4. [Carregar relacions: `JOIN FETCH` i `@EntityGraph`](#fetch)
5. [Projeccions: retornar només el que cal](#projeccions)
6. [Consultes natives](#natives)
7. [Modificacions massives: `@Modifying`](#modifying)
8. [`@NamedQuery`](#namedquery)
9. [Ordenació i paginació](#paginacio)
10. [Query by Example](#qbe)
11. [Altres: Specifications i QueryDSL](#altres)
12. [Cap a Catan](#catan)
13. [Errors típics](#errors)

---

<a id="maneres"></a>
## 1. Les maneres de consultar

| Manera | Qui escriu la consulta | Quan |
|---|---|---|
| **Mètodes heretats** (`findById`, `findAll`...) | Spring Data | CRUD |
| **Derivades del nom** (`findByEstudiNom`) | Spring Data, a partir del nom | Filtres senzills (1-3 condicions) |
| **`@Query` JPQL** | Nosaltres, en JPQL (l'HQL estàndard) | Joins, `JOIN FETCH`, agregats, projeccions, qualsevol cosa que no càpiga en un nom |
| **`@Query` nativa** | Nosaltres, en SQL de MySQL | Funcions específiques de MySQL, informes molt concrets |
| **`@NamedQuery`** | Nosaltres, a l'entitat | Consultes reutilitzades; com a la pràctica de Catan |
| **Query by Example** | Spring Data, a partir d'un objecte d'exemple | Cercadors amb filtres opcionals |
| Specifications, QueryDSL | Nosaltres, amb codi Java | Consultes dinàmiques complexes (no les fem) |

**JPQL o SQL?** JPQL treballa amb **entitats i atributs** (`Videojoc v`, `v.estudi.nom`). SQL treballa amb **taules i columnes** (`videojoc v`, `v.estudi_id`). JPQL és el mateix que l'HQL de les sessions d'Hibernate.

---

<a id="derivades"></a>
## 2. Consultes derivades del nom del mètode

Spring Data llegeix el **nom** del mètode i en genera la consulta. No cal escriure ni SQL ni implementació.

```java
List<Videojoc> findByEstudiNom(String nomEstudi);
```

```sql
-- el que genera (simplificat)
SELECT v.* FROM videojoc v JOIN estudi e ON e.id = v.estudi_id WHERE e.nom = ?
```

Si el nom no té sentit (un atribut que no existeix), **l'aplicació no arrenca**: l'error surt en arrencar, no quan es crida.

<a id="der-estructura"></a>
### 2.1 Estructura del nom

```
find   Distinct   Top3   By   EstudiNom   And   PreuLessThan   OrderBy   NomAsc
└─┬─┘  └──┬───┘   └┬─┘   └┬┘  └───┬───┘   └┬┘  └─────┬─────┘  └──┬──┘   └──┬─┘
prefix opcional  límit  separador  criteri  unió   criteri       ordre    atribut+sentit
```

| Prefix | Retorna |
|---|---|
| `find...By`, `read...By`, `get...By`, `query...By`, `search...By`, `stream...By` | Entitats (són sinònims) |
| `count...By` | `long` |
| `exists...By` | `boolean` |
| `delete...By`, `remove...By` | Esborra (cal transacció). Carrega les entitats i les esborra una a una |

Entre el prefix i el `By` hi pot anar qualsevol text (`findVideojocsByEstudiNom`): s'ignora, excepte `Distinct`, `First` i `Top`.

<a id="der-paraules"></a>
### 2.2 Paraules clau

| Paraula clau | Exemple | JPQL equivalent |
|---|---|---|
| (res), `Is`, `Equals` | `findByNom(n)` | `nom = ?1` |
| `Not` | `findByNomNot(n)` | `nom <> ?1` |
| `And` / `Or` | `findByNomAndEstudiNom(n, e)` | `nom = ?1 AND estudi.nom = ?2` |
| `Between` | `findByPreuBetween(min, max)` | `preu BETWEEN ?1 AND ?2` |
| `LessThan`, `LessThanEqual` | `findByPreuLessThan(p)` | `preu < ?1` |
| `GreaterThan`, `GreaterThanEqual` | `findByPreuGreaterThanEqual(p)` | `preu >= ?1` |
| `Before`, `After` | `findByDataLlancamentAfter(d)` | `dataLlancament > ?1` (dates) |
| `IsNull`, `IsNotNull` | `findByPareIsNull()` | `pare IS NULL` |
| `Like`, `NotLike` | `findByNomLike("%Mario%")` | `nom LIKE ?1` (el `%` el poseu vosaltres) |
| `StartingWith`, `EndingWith`, `Containing` | `findByNomContaining("Mario")` | `nom LIKE '%Mario%'` (el `%` el posa Spring) |
| `In`, `NotIn` | `findByNomIn(List.of("PC", "PS5"))` | `nom IN ?1` |
| `True`, `False` | `findByActivaTrue()` | `activa = true` |
| `IgnoreCase`, `AllIgnoreCase` | `findByNomIgnoreCase(n)` | `UPPER(nom) = UPPER(?1)` |
| `OrderBy...Asc/Desc` | `findByEstudiNomOrderByNomAsc(e)` | `ORDER BY nom ASC` |
| `Distinct` | `findDistinctByCategoriesNom(c)` | `SELECT DISTINCT` |

Exemples amb el model de videojocs:

```java
public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

	Optional<Categoria> findByNom(String nom);

	// Les arrels de l'arbre: pare_id IS NULL                                     (projecte)
	List<Categoria> findByPareIsNullOrderByNom();

	// Les filles d'una categoria, navegant per la relació: categoria.pare.nom    (projecte)
	List<Categoria> findByPareNomOrderByNom(String nomPare);

}

public interface LlancamentRepository extends JpaRepository<Llancament, Integer> {

	boolean existsByVideojocIdAndPlataformaId(Integer idVideojoc, Integer idPlataforma);

	// (projecte) GET /api/plataformes/Switch/barats?maxim=40
	List<Llancament> findByPlataformaNomAndPreuLessThanOrderByPreuAsc(String plataforma, BigDecimal preu);

	// (projecte) GET /api/plataformes/mes-cars
	List<Llancament> findTop3ByOrderByPreuDesc();

	List<Llancament> findByPreuBetween(BigDecimal min, BigDecimal max);
	List<Llancament> findByVideojocNom(String nomVideojoc);
	long countByPlataformaNom(String plataforma);

}
```

<a id="der-relacions"></a>
### 2.3 Navegar per relacions

Es concatenen els noms dels **atributs** pel camí de la relació:

| Mètode | Camí | Tipus de relació |
|---|---|---|
| `findByEstudiNom(n)` | `videojoc.estudi.nom` | N:1 |
| `findByCategoriesNom(n)` | `videojoc.categories[].nom` | N:M (un `JOIN`) |
| `findByLlancamentsPlataformaNom(n)` | `videojoc.llancaments[].plataforma.nom` | 1:N + N:1 |
| `existsByEstudiId(id)` | `videojoc.estudi.id` | N:1 (només mira la FK, no fa `JOIN`) |

Si hi ha ambigüitat (un atribut `estudiNom` i una relació `estudi` amb `nom`), es pot marcar el salt amb `_`: `findByEstudi_Nom`.

> Amb col·leccions (`findByCategoriesNom`), un videojoc que compleixi la condició per diverses files surt repetit. Si cal, `findDistinctBy...`.

<a id="der-retorn"></a>
### 2.4 Tipus de retorn

| Retorn | Si no hi ha resultats | Si n'hi ha més d'un |
|---|---|---|
| `List<T>` | Llista buida | Tots |
| `Optional<T>` | `Optional.empty()` | **Excepció** (`IncorrectResultSizeDataAccessException`) |
| `T` | `null` | Excepció |
| `Stream<T>` | Stream buit | Tots, a mesura que es llegeixen. **Cal transacció** i tancar-lo (`try-with-resources`) |
| `Page<T>`, `Slice<T>` | Pàgina buida | Una pàgina ([§9](#paginacio)) |
| `long` (amb `count`) | 0 | |
| `boolean` (amb `exists`) | `false` | |

Preferiu `Optional<T>` a `T` per a un sol resultat: obliga qui crida a pensar el cas "no existeix" (`isPresent()` i `404`).

<a id="der-limitar"></a>
### 2.5 Limitar els resultats

```java
Optional<Llancament> findFirstByOrderByPreuAsc();           // el més barat
List<Llancament> findTop3ByOrderByPreuDesc();                // els 3 més cars
List<Llancament> findTop5ByPlataformaNomOrderByPreuAsc(String plataforma);
```

`First` i `Top` són sinònims. Sense número, 1.

<a id="der-quan"></a>
### 2.6 Quan deixar-les

```java
// Funciona, però ningú no el vol llegir
List<Llancament> findDistinctByVideojocCategoriesNomAndPlataformaNomAndPreuLessThanOrderByPreuAsc(...);
```

Quan el nom passa de dues o tres condicions, o cal `JOIN FETCH`, agregats o subconsultes: **`@Query`**.

---

<a id="jpql"></a>
## 3. `@Query` amb JPQL

```java
public interface VideojocRepository extends JpaRepository<Videojoc, Integer> {

    // Paràmetres amb nom (:categoria) + @Param: RECOMANAT
    @Query("""
            SELECT v FROM Videojoc v
            JOIN v.categories c
            WHERE c.nom = :categoria
            ORDER BY v.nom
            """)
    List<Videojoc> findPerCategoria(@Param("categoria") String categoria);

    // Paràmetres per posició (?1, ?2): funciona, però és fàcil confondre'ls
    @Query("SELECT v FROM Videojoc v WHERE v.estudi.nom = ?1 AND v.nom LIKE ?2")
    List<Videojoc> findPerEstudiINom(String estudi, String patro);
}
```

- El nom del mètode **ja no importa** (`findPerCategoria` podria dir-se `llistar`); la consulta és la de l'anotació.
- Els **text blocks** (`"""`) permeten escriure la consulta en diverses línies.
- La consulta es **valida en arrencar**: si hi ha un atribut mal escrit, l'aplicació no arrenca.
- Es fan servir noms d'**entitats i atributs**, no de taules: `Videojoc`, `v.categories`, no `videojoc_categoria`.

Tot el que sabeu d'HQL hi va: `JOIN`, `LEFT JOIN`, `WHERE`, `GROUP BY`, `HAVING`, subconsultes, `EXISTS`, `COUNT`, `AVG`, `MAX`...

```java
// Videojocs sense cap llançament
@Query("SELECT v FROM Videojoc v WHERE v.llancaments IS EMPTY")
List<Videojoc> findSenseLlancaments();

// Videojocs que tenen preu a TOTES les plataformes
@Query("""
        SELECT v FROM Videojoc v
        WHERE SIZE(v.llancaments) = (SELECT COUNT(p) FROM Plataforma p)
        """)
List<Videojoc> findATotesLesPlataformes();

// Estudis amb més de N videojocs
@Query("""
        SELECT e FROM Estudi e
        WHERE (SELECT COUNT(v) FROM Videojoc v WHERE v.estudi = e) > :minim
        """)
List<Estudi> findAmbMesDe(@Param("minim") long minim);
```

---

<a id="fetch"></a>
## 4. Carregar relacions: `JOIN FETCH` i `@EntityGraph`

Si per mostrar tots els videojocs amb el detall fem `findAll()` i després `VideojocResposta.de(v)` per a cada joc, hi ha una `SELECT` dels videojocs i després **una `SELECT` de llançaments per a cada videojoc** (són LAZY). És el problema **N+1**: amb 5 jocs, 6 consultes; amb 500, 501.

Passa també amb relacions EAGER: en una consulta (JPQL o derivada), Hibernate carrega les relacions EAGER amb **una `SELECT` a part** per a cada fila.

### `JOIN FETCH`

El mateix que a la sessió del DAO:

```java
// llistarPerCategoria del DAO: els videojocs d'una categoria, amb l'estudi ja carregat
@Query("""
        SELECT v FROM Videojoc v
        JOIN FETCH v.estudi
        JOIN v.categories c
        WHERE c.nom = :categoria
        ORDER BY v.nom
        """)
List<Videojoc> llistarPerCategoria(@Param("categoria") String categoria);

// Tots els videojocs amb els llançaments i la plataforma de cada llançament: UNA consulta
@Query("""
        SELECT DISTINCT v FROM Videojoc v
        LEFT JOIN FETCH v.llancaments l
        LEFT JOIN FETCH l.plataforma
        ORDER BY v.nom
        """)
List<Videojoc> findAllAmbLlancaments();
```

**Regla** (la de la sessió DAO): `FETCH` per al que es farà servir **després**; `JOIN` simple per al que només serveix per filtrar. `LEFT JOIN FETCH` si hi pot haver entitats sense cap element a la relació (un joc sense llançaments).

### `@EntityGraph`

Fa el mateix sense escriure la consulta: s'afegeix a un mètode derivat o heretat i diu quines relacions s'han de portar:

```java
@EntityGraph(attributePaths = { "llancaments", "llancaments.plataforma" })
List<Videojoc> findAllByOrderByNomAsc();

@EntityGraph(attributePaths = { "llancaments" })
Optional<Videojoc> findWithLlancamentsById(Integer id);
```

**(projecte)** `llistarComplet()` (`GET /api/videojocs/complet`) fa servir `findAllByOrderByNomAsc()`, i `mostrar(id)` fa servir `findWithLlancamentsById(id)`: els llançaments arriben a la mateixa consulta.

```java
@Transactional(readOnly = true)
public ResponseEntity<List<VideojocResposta>> llistarComplet() {
	ArrayList<VideojocResposta> resultat = new ArrayList<>();
	for (Videojoc v : repository.findAllByOrderByNomAsc()) {
		resultat.add(VideojocResposta.de(v));
	}
	return ResponseEntity.ok()
			.header("Header-Customitzat", "Total videojocs: " + resultat.size())
			.body(resultat);
}
```

> Per què no hi posem també `categories` a l'`@EntityGraph`? Perquè `llancaments` és una `List` i `categories` un `Set`: el `JOIN` de totes dues multiplica les files i la `List` acabaria amb llançaments **repetits**. Les categories (EAGER) es carreguen amb consultes a part. Si cal reduir-les, `spring.jpa.properties.hibernate.default_batch_fetch_size=20` fa que Hibernate les porti en grups en lloc d'una a una.

### Limitacions

| Situació | Què passa |
|---|---|
| `JOIN FETCH` de **dues col·leccions `List`** alhora | `MultipleBagFetchException` (sessió de relacions). Una de les dues ha de ser `Set`, o fer dues consultes |
| `JOIN FETCH` d'una col·lecció + paginació | Hibernate pagina **en memòria** (porta totes les files) i avisa amb `HHH90003004`. Per paginar, primer els ids i després el `FETCH` |
| `JOIN FETCH` + `WHERE` sobre la mateixa col·lecció | La col·lecció només conté els elements que compleixen el `WHERE`. Per filtrar, un `JOIN` a part (com a `llistarPerCategoria`) |

---

<a id="projeccions"></a>
## 5. Projeccions: retornar només el que cal

No sempre cal carregar l'entitat sencera.

### Un sol valor

```java
// getNomFromID del DAO
@Query("SELECT v.nom FROM Videojoc v WHERE v.id = :id")
Optional<String> findNomById(@Param("id") Integer id);

@Query("SELECT MAX(l.preu) FROM Llancament l WHERE l.plataforma.nom = :plataforma")
Optional<BigDecimal> preuMaxim(@Param("plataforma") String plataforma);
```

### Un `record` (DTO) amb `SELECT new`

**(projecte)** `GET /api/plataformes/estadistiques`:

```java
package cat.iesabadell.videojocs.dto;

/*
 * L'ordre i els tipus del constructor han de coincidir amb els de la consulta:
 *   COUNT -> Long, MIN/MAX d'un BigDecimal -> BigDecimal, AVG -> Double
 */
public record PreusPlataforma(String plataforma, Long jocs, BigDecimal minim, BigDecimal maxim, Double mitjana) {
}
```

```java
// PlataformaRepository
@Query("""
		SELECT new cat.iesabadell.videojocs.dto.PreusPlataforma(p.nom, COUNT(l), MIN(l.preu), MAX(l.preu), AVG(l.preu))
		FROM Llancament l JOIN l.plataforma p
		GROUP BY p.nom
		ORDER BY p.nom
		""")
List<PreusPlataforma> estadistiquesPreus();
```

```json
[
  { "plataforma": "PC", "jocs": 3, "minim": 24.99, "maxim": 59.99, "mitjana": 38.323333 },
  ...
]
```

- El nom de la classe va **complet** (amb el paquet).
- Els tipus del constructor han de coincidir amb els de la consulta: `COUNT` retorna `Long`, `AVG` retorna `Double`, `MIN`/`MAX` el tipus de l'atribut.
- El resultat ja és un DTO: es pot retornar directament des del controlador, sense problemes de LAZY ni de bucles.

### Una interfície

Spring Data crea un objecte que implementa la interfície amb els getters que demanem. Funciona amb consultes derivades:

```java
public interface VideojocNom {
    Integer getId();
    String getNom();
}

// Només SELECT id, nom: no carrega l'entitat ni les relacions
List<VideojocNom> findByEstudiNomOrderByNom(String estudi);
```

I amb consultes natives, fent coincidir els **àlies** de les columnes amb els getters ([§6](#natives)).

| Projecció | Com | Quan |
|---|---|---|
| Valor | `SELECT v.nom` | Un sol camp |
| `record` | `SELECT new ...(...)` | Agregats, informes, respostes de l'API |
| Interfície | Getters a una interfície | Subconjunt de camps amb consultes derivades o natives |

---

<a id="natives"></a>
## 6. Consultes natives

SQL de MySQL directament. Es fan servir noms de **taules i columnes** reals (amb l'estratègia de noms de Spring Boot: *snake_case*).

```java
public interface JocsPerPlataforma {
    String getPlataforma();
    Long getJocs();
}

@Query(value = """
        SELECT p.nom AS plataforma, COUNT(*) AS jocs
        FROM llancament l
        JOIN plataforma p ON p.id = l.plataforma_id
        GROUP BY p.nom
        ORDER BY jocs DESC
        """, nativeQuery = true)
List<JocsPerPlataforma> jocsPerPlataforma();
```

| A favor | En contra |
|---|---|
| Funcions pròpies de MySQL (`GROUP_CONCAT`, funcions de dates, `JSON_...`) | Lligada a MySQL: amb una altra BD pot no funcionar |
| SQL que ja tenim escrit i provat | No es valida en arrencar: els errors surten en executar |
| | Si canvien les entitats (un nom d'atribut), la consulta no se n'assabenta |

Primer JPQL; nativa només quan JPQL no arriba.

---

<a id="modifying"></a>
## 7. Modificacions massives: `@Modifying`

Un `UPDATE` o `DELETE` que afecta moltes files amb **una sola sentència**, sense carregar les entitats:

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE Llancament l SET l.preu = l.preu * :factor WHERE l.plataforma.id = :idPlataforma")
int aplicarFactor(@Param("idPlataforma") Integer idPlataforma, @Param("factor") BigDecimal factor);
```

**(projecte)** `POST /api/plataformes/{nom}/rebaixes?percentatge=10`:

```java
// PlataformaService
@Transactional
public ResponseEntity<String> rebaixes(String nom, int percentatge) {
	Optional<Plataforma> p = repository.findByNom(nom);
	if (!p.isPresent()) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.header("Header-Customitzat", "PLATAFORMA NO TROBADA")
				.body("NO EXISTEIX LA PLATAFORMA " + nom);
	}
	// 10% de descompte -> multiplicar per 0.90
	BigDecimal factor = BigDecimal.ONE.subtract(
			BigDecimal.valueOf(percentatge).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
	int modificats = repository.aplicarFactor(p.get().getId(), factor);   // retorna les files modificades
	return ResponseEntity.ok()
			.header("Header-Customitzat", "Rebaixes aplicades a " + nom)
			.body("S'HAN ABAIXAT " + modificats + " PREUS UN " + percentatge + "% A " + nom);
}
```

| Cal | Per què |
|---|---|
| `@Modifying` | Sense, Spring l'executa com una `SELECT` i falla |
| Una transacció (`@Transactional` al servei) | Sense, `TransactionRequiredException` |
| `clearAutomatically = true` | L'`UPDATE` va directament a la BD: les entitats que ja hi havia carregades a la transacció tindrien el preu antic. Això les buida |

**`@Modifying` o *dirty checking*?** Compareu-ho amb `abaixaPreuPerCategoria` de la sessió 3:

| | Bucle + *dirty checking* | `@Modifying` |
|---|---|---|
| Sentències | 1 `SELECT` + 1 `UPDATE` per fila | 1 `UPDATE` |
| Lògica per fila (preu mínim 0, comprovacions) | En Java, fàcil | En SQL (`CASE`, `GREATEST`...) |
| Validacions de l'entitat, *cascades*, `@Version` | Sí | **No**: se les salta |
| Entitats carregades a la transacció | Al dia | Desactualitzades (per això `clearAutomatically`) |

Amb poques files i lògica de negoci, *dirty checking*. Amb moltes files i una operació simple, `@Modifying`.

---

<a id="namedquery"></a>
## 8. `@NamedQuery`

Una consulta JPQL amb nom, definida **a l'entitat**. És el que demana l'enunciat de Catan per buscar per codi.

```java
@Entity
@Table(name = "videojoc")
@NamedQuery(name = "Videojoc.findPerEstudiOrdenats",
            query = "SELECT v FROM Videojoc v WHERE v.estudi.nom = :estudi ORDER BY v.nom")
public class Videojoc { ... }
```

Spring Data la fa servir **sola** si el repositori té un mètode amb el mateix nom (`NomEntitat.nomMetode`):

```java
public interface VideojocRepository extends JpaRepository<Videojoc, Integer> {
    List<Videojoc> findPerEstudiOrdenats(@Param("estudi") String estudi);   // -> Videojoc.findPerEstudiOrdenats
}
```

O explícitament:

```java
@Query(name = "Videojoc.findPerEstudiOrdenats")
List<Videojoc> perEstudi(@Param("estudi") String estudi);
```

Si veniu de la part d'Hibernate de Catan, les `@NamedQuery` que ja teniu a les entitats es poden cridar així des dels repositoris, sense tornar-les a escriure.

---

<a id="paginacio"></a>
## 9. Ordenació i paginació

### Ordenar

```java
repository.findAll(Sort.by("nom"));
repository.findAll(Sort.by(Sort.Direction.DESC, "nom"));
lRepository.findAll(Sort.by("plataforma.nom").and(Sort.by(Sort.Direction.DESC, "preu")));

// Als mètodes derivats i @Query, com a últim paràmetre
List<Videojoc> findByEstudiNom(String estudi, Sort sort);
```

### Paginar

```java
// Al repositori: qualsevol consulta accepta un Pageable i pot retornar Page
Page<Videojoc> findByEstudiNom(String estudi, Pageable pageable);
```

```java
// Pàgina 0 (la primera), 5 elements, ordenats per nom
Page<Videojoc> pagina = repository.findAll(PageRequest.of(0, 5, Sort.by("nom")));

pagina.getContent();         // List<Videojoc> d'aquesta pàgina
pagina.getNumber();          // 0
pagina.getSize();            // 5
pagina.getTotalElements();   // total de files (fa un SELECT COUNT a part)
pagina.getTotalPages();
pagina.hasNext();
```

| | `Page<T>` | `Slice<T>` |
|---|---|---|
| Sap el total | Sí (`COUNT` extra) | No |
| Sap si n'hi ha més | Sí | Sí (demana un element de més) |
| Per a | Paginació amb números de pàgina | *Scroll* infinit, "carregar més" |

### A l'API

Retornar `Page` directament genera un JSON amb molts camps interns i Spring Data avisa que la seva estructura no és estable. Fem un DTO de pàgina:

```java
public record PaginaResposta<T>(List<T> contingut, int pagina, int mida, long total, int totalPagines) {

    public static <T> PaginaResposta<T> de(Page<T> p) {
        return new PaginaResposta<>(p.getContent(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages());
    }
}
```

**(projecte)** `GET /api/videojocs/pagina?pagina=1&mida=2`:

```java
// Servei. Page.map converteix cada Videojoc de la pàgina en un VideojocResum
@Transactional(readOnly = true)
public ResponseEntity<PaginaResposta<VideojocResum>> pagina(int pagina, int mida) {
	Page<Videojoc> p = repository.findAll(PageRequest.of(pagina, mida, Sort.by("nom")));
	return ResponseEntity.ok()
			.header("Header-Customitzat", "Pagina " + pagina + " de " + p.getTotalPages())
			.body(PaginaResposta.de(p.map(VideojocResum::de)));
}

// Controlador
@GetMapping(path="/pagina")
public ResponseEntity<PaginaResposta<VideojocResum>> pagina(
		@RequestParam(defaultValue = "0") @Min(value = 0, message = "La pàgina comença a 0") int pagina,
		@RequestParam(defaultValue = "5") @Min(value = 1, message = "La mida mínima és 1")
				@Max(value = 50, message = "La mida màxima és 50") int mida) {
	return service.pagina(pagina, mida);
}
```

```json
{
  "contingut": [
    { "id": 2, "nom": "Cyberpunk 2077", "estudi": "CD Projekt Red" },
    { "id": 3, "nom": "Hades", "estudi": "Supergiant Games" }
  ],
  "pagina": 1,
  "mida": 2,
  "total": 5,
  "totalPagines": 3
}
```

> Spring també pot omplir un paràmetre `Pageable` del controlador directament a partir de `?page=1&size=2&sort=nom,asc`. És còmode, però deixa que el client ordeni per qualsevol atribut i demani pàgines de qualsevol mida; amb paràmetres propis es controla què s'accepta.

---

<a id="qbe"></a>
## 10. Query by Example

Es busca a partir d'un **objecte d'exemple** (*probe*): els atributs que no són `null` són els criteris. `JpaRepository` ja ho porta (`QueryByExampleExecutor`).

```java
// Plataformes que comencen per "ps", sense distingir majúscules
Plataforma mostra = new Plataforma("ps");
ExampleMatcher matcher = ExampleMatcher.matching()
        .withMatcher("nom", m -> m.startsWith().ignoreCase());

List<Plataforma> resultat = plataformaRepo.findAll(Example.of(mostra, matcher));
```

Útil per a cercadors amb filtres opcionals (els que l'usuari no omple queden a `null`). Limitacions: només atributs de l'entitat (no relacions de col·lecció), sense `OR` entre camps diferents ni rangs. **Compte** amb els tipus primitius: un `int` val 0, no `null`, i entra com a criteri (un motiu més per fer servir `Integer`).

---

<a id="altres"></a>
## 11. Altres: Specifications i QueryDSL

Per a consultes **dinàmiques** (l'usuari tria quins filtres aplica i com es combinen), hi ha dues opcions que no fem a classe:

| | Què és |
|---|---|
| **Specifications** (`JpaSpecificationExecutor`) | Criteris com a objectes Java que es combinen amb `and()` / `or()`. Ve amb Spring Data JPA |
| **QueryDSL** | Llibreria externa que genera classes (`QVideojoc`) per escriure consultes tipades: `videojoc.estudi.nom.eq("Nintendo")`. Cal un plugin al `pom.xml` |

Per a la majoria de casos, una `@Query` amb paràmetres opcionals és suficient:

```java
@Query("""
        SELECT l FROM Llancament l
        WHERE (:plataforma IS NULL OR l.plataforma.nom = :plataforma)
          AND (:maxim IS NULL OR l.preu <= :maxim)
        """)
List<Llancament> cercar(@Param("plataforma") String plataforma, @Param("maxim") BigDecimal maxim);
```

---

<a id="catan"></a>
## 12. Cap a Catan

| A Catan | Peça d'avui |
|---|---|
| Buscar hexàgon, vèrtex o aresta per codi amb `@NamedQuery` | [§8](#namedquery): el repositori crida la `@NamedQuery` de l'entitat |
| Buscar per partida i codi | Derivada: `findByPartidaIdAndCodi(...)` ([§2.3](#der-relacions)) |
| `VertexsDisponibles`: resolt amb JPQL, no recorrent tots els vèrtexs en Java | `@Query` amb subconsultes `NOT EXISTS` ([§3](#jpql)) |
| `GET /partida/{id}/tauler`: hexàgons amb les construccions adjacents | `JOIN FETCH` o `@EntityGraph` per evitar N+1 ([§4](#fetch)), i DTO |
| Recursos d'un jugador, recompte de punts | Projeccions i agregats ([§5](#projeccions)) |
| Producció després dels daus: tots els assentaments adjacents als hexàgons amb el número | Una `@Query` amb `JOIN` en lloc de bucles niats en Java |

---

<a id="errors"></a>
## 13. Errors típics

| Error | Causa | Solució |
|---|---|---|
| `No property 'nmo' found for type 'Videojoc'` (en arrencar) | Un mètode derivat fa servir un atribut que no existeix, o mal escrit | El nom ha de coincidir amb l'**atribut Java**, no amb la columna |
| `Could not resolve attribute 'estudi_id'` / `Could not interpret path expression` | JPQL amb noms de columnes o taules | JPQL: entitats i atributs (`v.estudi.id`) ([§1](#maneres)) |
| `IncorrectResultSizeDataAccessException: query did not return a unique result` | `Optional<T>` o `T` amb una consulta que en retorna diversos | `List<T>`, o afinar la consulta ([§2.4](#der-retorn)) |
| `No parameter binding found for name 'categoria'` | Falta `@Param("categoria")` o el nom no coincideix | [§3](#jpql) |
| `Query executed via 'getResultList()' or 'getSingleResult()' must be a 'select' query` | `UPDATE`/`DELETE` sense `@Modifying` | [§7](#modifying) |
| `TransactionRequiredException: Executing an update/delete query` | `@Modifying` cridat fora de transacció | `@Transactional` al servei ([§7](#modifying)) |
| Preus antics després d'un `@Modifying` | Les entitats ja carregades no s'actualitzen | `clearAutomatically = true` ([§7](#modifying)) |
| `MultipleBagFetchException` | `JOIN FETCH` de dues `List` | Una `Set`, o dues consultes ([§4](#fetch)) |
| `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory` | `JOIN FETCH` d'una col·lecció + `Pageable` | [§4](#fetch) (limitacions) |
| Resultats repetits | `JOIN` amb una col·lecció | `DISTINCT` ([§2.3](#der-relacions)) |
| `Cannot instantiate class ... PreusPlataforma` / `Missing constructor` | `SELECT new` amb tipus o ordre de paràmetres que no coincideixen amb el `record` | `AVG` → `Double`, `COUNT` → `Long`; mateix ordre ([§5](#projeccions)) |
| Nativa: `Table 'videojocs_spring.Videojoc' doesn't exist` | SQL amb el nom de la classe en lloc del de la taula | Noms reals de taula i columna (*snake_case*) ([§6](#natives)) |
| `You're trying to execute a streaming query method without a surrounding transaction` | Mètode que retorna `Stream` cridat fora de transacció | `@Transactional(readOnly = true)` ([§2.4](#der-retorn)) |
