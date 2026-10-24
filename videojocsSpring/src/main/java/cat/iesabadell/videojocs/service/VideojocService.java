package cat.iesabadell.videojocs.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import cat.iesabadell.videojocs.dto.PaginaResposta;
import cat.iesabadell.videojocs.dto.VideojocPeticio;
import cat.iesabadell.videojocs.dto.VideojocResposta;
import cat.iesabadell.videojocs.dto.VideojocResum;
import cat.iesabadell.videojocs.model.Categoria;
import cat.iesabadell.videojocs.model.Estudi;
import cat.iesabadell.videojocs.model.FitxaTecnica;
import cat.iesabadell.videojocs.model.Llancament;
import cat.iesabadell.videojocs.model.Plataforma;
import cat.iesabadell.videojocs.model.Videojoc;
import cat.iesabadell.videojocs.repository.CategoriaRepository;
import cat.iesabadell.videojocs.repository.EstudiRepository;
import cat.iesabadell.videojocs.repository.LlancamentRepository;
import cat.iesabadell.videojocs.repository.PlataformaRepository;
import cat.iesabadell.videojocs.repository.VideojocRepository;

/*
 * El SERVEI FORT (sessió 3): l'equivalent del VideojocDao fort d'Hibernate.
 * Fa servir els repositoris que calgui (són prims, gairebé buits) i cada mètode públic és
 * UNA operació completa i UNA transacció.
 *
 *   DAO d'Hibernate                              Spring
 *   ------------------------------------------   ------------------------------------------
 *   begin / commit / rollback a cada mètode      @Transactional
 *   mètodes privats que reben la Session         no cal passar res: la transacció es propaga
 *   retornava text o entitats                    retorna ResponseEntity amb DTO
 *
 * SORTIDA: DTO (VideojocResum, VideojocResposta), MAI l'entitat Videojoc:
 * té llançaments LAZY que fora d'aquest servei petarien (open-in-view=false).
 * Els DTO es construeixen DINS dels mètodes @Transactional.
 *
 * Tot es gestiona des de Videojoc, com al VideojocDao: no hi ha EstudiService ni CategoriaService.
 * Les altres entitats es busquen pel nom (estudi, categories, plataformes) o es desen per
 * cascade des del videojoc (fitxa tècnica, llançaments).
 * (PlataformaService és l'exemple introductori de la sessió 1 i es manté a part.)
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

	// ===================== LECTURA =====================

	/*
	 * Llistat curt (VideojocResum). Si arriba una categoria, només els d'aquella categoria.
	 * readOnly: només llegim; Hibernate no comprova canvis en tancar.
	 */
	@Transactional(readOnly = true)
	public ResponseEntity<List<VideojocResum>> llistar(String categoria) {
		List<Videojoc> videojocs;
		if (categoria == null) {
			videojocs = repository.findAll(Sort.by("nom"));
		} else {
			// @Query amb JOIN FETCH (sessió 4)
			videojocs = repository.llistarPerCategoria(categoria);
		}

		ArrayList<VideojocResum> resultat = new ArrayList<>();
		for (Videojoc v : videojocs) {
			resultat.add(VideojocResum.de(v));
		}
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Total videojocs: " + resultat.size())
				.body(resultat);
	}

	/*
	 * Llistat complet (VideojocResposta). Cada DTO llegeix els llançaments (LAZY).
	 * Amb findAll() serien 1 + N SELECT (problema N+1). Amb @EntityGraph, els llançaments
	 * arriben a la mateixa consulta (sessió 4). Mireu-ho al log amb show-sql.
	 */
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

	@Transactional(readOnly = true)
	public ResponseEntity<?> mostrar(int id) {
		// @EntityGraph: el videojoc arriba amb els llançaments ja carregats (una consulta menys)
		Optional<Videojoc> v = repository.findWithLlancamentsById(id);
		if (!v.isPresent()) {
			return noTrobat(id);
		}
		// El DTO es construeix AQUÍ, amb la transacció oberta: els llançaments LAZY es poden llegir
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Dades videojoc ID: " + id)
				.body(VideojocResposta.de(v.get()));
	}

	// Paginació (sessió 4). Page.map converteix cada Videojoc de la pàgina en un VideojocResum
	@Transactional(readOnly = true)
	public ResponseEntity<PaginaResposta<VideojocResum>> pagina(int pagina, int mida) {
		Page<Videojoc> p = repository.findAll(PageRequest.of(pagina, mida, Sort.by("nom")));
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Pagina " + pagina + " de " + p.getTotalPages())
				.body(PaginaResposta.de(p.map(VideojocResum::de)));
	}

	// ===================== MODIFICAR RELACIONS =====================

	/*
	 * N:M PURA: afegir a la col·lecció del costat propietari (Videojoc.categories).
	 * Cap save: el videojoc és GESTIONAT -> al commit, INSERT INTO videojoc_categoria.
	 */
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
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Categoria afegida al videojoc " + idVideojoc)
				.body(VideojocResposta.de(v.get()));
	}

	// N:M PURA: treure de la col·lecció -> DELETE FROM videojoc_categoria (només aquesta fila: és un Set)
	@Transactional
	public ResponseEntity<?> treureCategoria(int idVideojoc, String nomCategoria) {
		Optional<Videojoc> v = repository.findById(idVideojoc);
		Optional<Categoria> c = cRepository.findByNom(nomCategoria);
		if (!v.isPresent()) {
			return noTrobat(idVideojoc);
		}
		if (!c.isPresent() || !v.get().getCategories().contains(c.get())) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.header("Header-Customitzat", "CATEGORIA NO TROBADA")
					.body(v.get().getNom() + " NO ÉS DE LA CATEGORIA " + nomCategoria);
		}
		v.get().treureCategoria(c.get());
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Categoria treta del videojoc " + idVideojoc)
				.body(VideojocResposta.de(v.get()));
	}

	/*
	 * N:M AMB ATRIBUTS: un Llancament nou, que es desa per cascade des del videojoc.
	 * Comprovem el duplicat abans: si no, el UNIQUE(videojoc_id, plataforma_id) petaria al commit.
	 */
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
		v.get().afegirLlancament(p.get(), preu);
		return ResponseEntity.status(HttpStatus.CREATED)
				.header("Header-Customitzat", "Llancament afegit al videojoc " + idVideojoc)
				.body(VideojocResposta.de(v.get()));
	}

	// ===================== OPERACIONS DE NEGOCI =====================

	/*
	 * Abaixa el preu de tots els llançaments dels videojocs d'una categoria
	 * I DE TOTES LES SEVES SUBCATEGORIES. Bucle + dirty checking: cap saveAll.
	 */
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
		// En sortir: commit -> un UPDATE per cada llançament modificat
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Descompte aplicat per categoria")
				.body("S'HAN ABAIXAT " + llancaments.size() + " PREUS DE LA CATEGORIA " + nomCategoria
						+ " I LES SEVES SUBCATEGORIES");
	}

	/*
	 * Crea un videojoc complet (fitxa, categories i preus) en UNA transacció.
	 *
	 * COMPTE AMB EL ROLLBACK. Spring només fa rollback si el mètode LLANÇA una RuntimeException.
	 * Aquí, com a tot el servei, els errors es RETORNEN (ResponseEntity amb 404, 409...).
	 * Si retornem un error DESPRÉS d'haver modificat la BD, Spring faria COMMIT del que s'ha fet.
	 * Dues maneres d'evitar-ho:
	 *   1. Comprovar-ho tot ABANS de modificar res (el nom repetit, l'estudi).
	 *   2. Si l'error es troba DESPRÉS (aquí, les categories i plataformes), marcar la
	 *      transacció com a rollback-only abans de retornar: és el rollback() explícit del DAO.
	 */
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

	// Esborra el videojoc i, per cascade, la fitxa, els llançaments i les files de videojoc_categoria
	@Transactional
	public ResponseEntity<String> esborrar(int id) {
		Optional<Videojoc> v = repository.findById(id);
		if (!v.isPresent()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.header("Header-Customitzat", "ID " + id + " NO TROBAT")
					.body("NO EXISTEIX CAP VIDEOJOC AMB ID " + id);
		}
		repository.delete(v.get());
		return ResponseEntity.noContent()
				.header("Header-Customitzat", "Videojoc " + id + " esborrat")
				.build();
	}

	// ===================== CÀRREGA INICIAL DES DELS CSV (sessió 3) =====================

	/*
	 * El carregarDades() del VideojocDao. Mateix ordre i mateixes dues passades per a les categories:
	 *   session.persist(x)  ->  repository.save(x)
	 *   begin / commit      ->  @Transactional
	 * UNA transacció: si un fitxer té un error, no es desa res.
	 * rollbackFor = Exception.class: IOException és CHECKED, i per defecte Spring
	 * només fa rollback amb RuntimeException. Sense això, faria commit del que s'hagués carregat.
	 * És públic al servei (i no dins del CommandLineRunner) perquè es pugui cridar també des d'un
	 * endpoint: a Catan, el tauler es carrega dels CSV quan es crea la partida (POST /partida).
	 */
	@Transactional(rollbackFor = Exception.class)
	public int carregarDades() throws IOException {
		// estudis.csv -> nom
		HashMap<String, Estudi> estudis = new HashMap<>();
		for (String[] fila : llegirCsv("estudis.csv")) {
			estudis.put(fila[0], eRepository.save(new Estudi(fila[0])));
		}

		// plataformes.csv -> nom
		// Les plataformes ja poden existir (les de la sessió 1): si hi són, les fem servir; si no, les creem
		HashMap<String, Plataforma> plataformes = new HashMap<>();
		for (String[] fila : llegirCsv("plataformes.csv")) {
			Plataforma p = pRepository.findByNom(fila[0]).orElseGet(() -> pRepository.save(new Plataforma(fila[0])));
			plataformes.put(fila[0], p);
		}

		/*
		 * categories.csv -> nom,pare (pare buit = arrel)
		 * DUES PASSADES perquè una filla pot aparèixer ABANS que el seu pare:
		 *   1a: crear totes les categories
		 *   2a: enllaçar cada filla amb el seu pare
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

		/*
		 * videojocs.csv -> nom,estudi,motor
		 *   estudi: clau d'una altra entitat (N:1) -> la busquem al HashMap
		 *   motor : la 1:1 va a la mateixa fila. Buit = sense fitxa tècnica
		 */
		HashMap<String, Videojoc> videojocs = new HashMap<>();
		for (String[] fila : llegirCsv("videojocs.csv")) {
			Videojoc v = new Videojoc(fila[0]);
			obtenir(estudis, fila[1], "videojocs.csv").afegirVideojoc(v);
			if (!fila[2].isEmpty()) {
				v.setFitxa(new FitxaTecnica(fila[2]));
			}
			videojocs.put(fila[0], repository.save(v));
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

	// ===================== MÈTODES PRIVATS =====================

	// Sense @Transactional: sempre els crida un mètode públic que ja en té

	private void recollirSubarbre(Categoria c, Set<Categoria> resultat) {
		resultat.add(c);
		for (Categoria filla : c.getFilles()) {
			recollirSubarbre(filla, resultat);
		}
	}

	private ResponseEntity<?> noTrobat(int id) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.header("Header-Customitzat", "ID " + id + " NO TROBAT")
				.body("NO EXISTEIX CAP VIDEOJOC AMB ID " + id);
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
	 * ClassPathResource: funciona tant des d'Eclipse com dins del .jar (un FileReader amb la ruta
	 * "src/main/resources/..." només funcionaria des d'Eclipse).
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
