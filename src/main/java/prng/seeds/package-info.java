/**
 * This package provides storage for seed entropy. Each stored seed has a unique
 * name. The seed may contain any structured information, but a typical seed
 * will just contain an entropy block. An entropy block is an opaque data block
 * that represents information unknown to an opponent. The entropy block is
 * passed through a one-way cipher on every save and every load. The cipher
 * preserves the unknown information, but because the cipher key is unknown the
 * actual bits used as a seed are also unknown. Examining the file neither
 * before it is used nor after will reveal the actual bits used for the seed.
 * 
 * @author Simon Greatrix
 */
package prng.seeds;

