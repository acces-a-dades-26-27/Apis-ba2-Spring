package cat.iesabadell.videojocs.dto;

import java.math.BigDecimal;

/*
 * PROJECCIÓ amb record (sessió 4): el resultat d'una consulta amb SELECT new.
 * L'ordre i els tipus del constructor han de coincidir amb els de la consulta:
 *   COUNT -> Long, MIN/MAX d'un BigDecimal -> BigDecimal, AVG -> Double
 */
public record PreusPlataforma(String plataforma, Long jocs, BigDecimal minim, BigDecimal maxim, Double mitjana) {
}
