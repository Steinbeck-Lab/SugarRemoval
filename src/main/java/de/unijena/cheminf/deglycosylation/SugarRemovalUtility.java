/**
 * TODO: Add license header
 */
package de.unijena.cheminf.deglycosylation;

/**
 * TODO:
 * -
 */

import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.graph.ConnectivityChecker;
import org.openscience.cdk.graph.GraphUtil;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomContainerSet;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.isomorphism.DfPattern;
import org.openscience.cdk.isomorphism.UniversalIsomorphismTester;
import org.openscience.cdk.ringsearch.RingSearch;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.BondManipulator;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO: Add doc
 */
public final class SugarRemovalUtility {
    //<editor-fold desc="Enum StructuresToKeepMode">
    /**
     * TODO
     */
    public static enum StructuresToKeepMode {
        /**
         * TODO
         */
        ALL (0),

        /**
         * TODO
         */
        HEAVY_ATOM_COUNT (5),

        /**
         * TODO
         */
        MOLECULAR_WEIGHT (60);

        /**
         * TODO
         */
        private final int defaultThreshold;

        /**
         * TODO
         */
        StructuresToKeepMode(int aDefaultValue) {
            this.defaultThreshold = aDefaultValue;
        }

        /**
         * TODO
         */
        public int getDefaultThreshold() {
            return this.defaultThreshold;
        }
    }
    //</editor-fold>
    //
    //<editor-fold desc="Private static final constants">
    /**
     * TODO: Add doc, add names of sugars
     */
    private static final String[] LINEAR_SUGARS_SMILES = {
            "C(C(C(C(C(C=O)O)O)O)O)O",
            "C(C(CC(C(CO)O)O)O)(O)=O",
            "C(C(C(CC(=O)O)O)O)O",
            "C(C(C(C(C(CO)O)O)O)=O)O",
            "C(C(C(C(C(CO)O)O)O)O)O",
            "C(C(C(C(CC=O)O)O)O)O",
            "OCC(O)C(O)C(O)C(O)CO",
            "O=CC(O)C(O)C(O)C(O)CO",
            "CCCCC(O)C(=O)O",
            "CC(=O)CC(=O)CCC(=O)O",
            "O=C(O)CC(O)CC(=O)O",
            "O=C(O)C(=O)C(=O)C(O)C(O)CO",
            "O=C(O)CCC(O)C(=O)O",
            "O=CC(O)C(O)C(O)C(O)CO",
            "O=C(CO)C(O)C(O)CO"};

    /**
     * TODO: Add doc, add names/description
     */
    private static final String [] RING_SUGARS_SMILES = {
            "C1CCOC1",
            "C1CCOCC1",
            "C1CCCOCC1"};

    /**
     * TODO
     */
    private static final boolean DETECT_GLYCOSIDIC_BOND_DEFAULT = false;

    /**
     * TODO
     */
    private static final boolean REMOVE_ONLY_TERMINAL_DEFAULT = true;

    /**
     * TODO
     */
    private static final StructuresToKeepMode STRUCTURES_TO_KEEP_MODE_DEFAULT = StructuresToKeepMode.HEAVY_ATOM_COUNT;

    /**
     * TODO
     */
    private static final boolean INCLUDE_NR_OF_ATTACHED_OXYGEN_DEFAULT = true;

    /**
     * TODO
     */
    private static final double ATTACHED_OXYGENS_TO_ATOMS_IN_RING_RATIO_THRESHOLD_DEFAULT = 0.5;

    /**
     * TODO
     */
    private static final boolean REMOVE_LINEAR_SUGARS_IN_RING_DEFAULT = false;

    /**
     * Logger of this class
     */
    private static final Logger LOGGER = Logger.getLogger(SugarRemovalUtility.class.getName());
    //</editor-fold>
    //
    //<editor-fold desc="Private final variables">
    /**
     * TODO: Add doc
     */
    private final UniversalIsomorphismTester univIsomorphismTester;
    //</editor-fold>
    //
    //<editor-fold desc="Private variables">
    /**
     * TODO: Add doc
     */
    private List<IAtomContainer> linearSugars;

    /**
     * TODO: Add doc
     */
    private List<IAtomContainer> ringSugars;

    /**
     * TODO: Add doc
     */
    private List<DfPattern> linearSugarPatterns;

    /**
     * TODO: Add doc
     */
    private boolean detectGlycosidicBond;

    /**
     * TODO
     */
    private boolean removeOnlyTerminal;

    /**
     * TODO
     */
    private StructuresToKeepMode structuresToKeepMode;

    /**
     * TODO
     */
    private int mwOrHacThreshold;

    /**
     * TODO
     */
    private boolean includeNrOfAttachedOxygens;

    /**
     * TODO
     */
    private double attachedOxygensToAtomsInRingRatioThreshold;

    /**
     * TODO
     */
    private boolean removeLinearSugarsInRing;
    //</editor-fold>
    //
    //<editor-fold desc="Constructors">
    /**
     * TODO: Add doc, add parameters
     */
    public SugarRemovalUtility() {
        this.univIsomorphismTester = new UniversalIsomorphismTester();
        this.linearSugars = new ArrayList<>(SugarRemovalUtility.LINEAR_SUGARS_SMILES.length);
        this.ringSugars = new ArrayList<>(SugarRemovalUtility.RING_SUGARS_SMILES.length);
        this.linearSugarPatterns = new ArrayList<>(SugarRemovalUtility.LINEAR_SUGARS_SMILES.length);
        SmilesParser tmpSmilesParser = new SmilesParser(DefaultChemObjectBuilder.getInstance());
        //adding linear sugars to list
        for (String tmpSmiles : SugarRemovalUtility.LINEAR_SUGARS_SMILES) {
            try {
                this.linearSugars.add(tmpSmilesParser.parseSmiles(tmpSmiles));
            } catch (InvalidSmilesException anInvalidSmilesException) {
                SugarRemovalUtility.LOGGER.log(Level.WARNING, anInvalidSmilesException.toString(), anInvalidSmilesException);
            }
        }
        //adding ring sugars to list
        for (String tmpSmiles : SugarRemovalUtility.RING_SUGARS_SMILES) {
            try {
                this.ringSugars.add(tmpSmilesParser.parseSmiles(tmpSmiles));
            } catch (InvalidSmilesException anInvalidSmilesException) {
                SugarRemovalUtility.LOGGER.log(Level.WARNING, anInvalidSmilesException.toString(), anInvalidSmilesException);
            }
        }
        //parsing linear sugars into patterns
        for(IAtomContainer tmpSugarAC : this.linearSugars){
            this.linearSugarPatterns.add(DfPattern.findSubstructure(tmpSugarAC));
        }
        this.detectGlycosidicBond = SugarRemovalUtility.DETECT_GLYCOSIDIC_BOND_DEFAULT;
        this.removeOnlyTerminal = SugarRemovalUtility.REMOVE_ONLY_TERMINAL_DEFAULT;
        this.structuresToKeepMode = SugarRemovalUtility.STRUCTURES_TO_KEEP_MODE_DEFAULT;
        this.mwOrHacThreshold = this.structuresToKeepMode.defaultThreshold;
        this.includeNrOfAttachedOxygens = SugarRemovalUtility.INCLUDE_NR_OF_ATTACHED_OXYGEN_DEFAULT;
        this.attachedOxygensToAtomsInRingRatioThreshold = SugarRemovalUtility.ATTACHED_OXYGENS_TO_ATOMS_IN_RING_RATIO_THRESHOLD_DEFAULT;
        this.removeLinearSugarsInRing = SugarRemovalUtility.REMOVE_LINEAR_SUGARS_IN_RING_DEFAULT;
    }
    //</editor-fold>
    //
    //<editor-fold desc="Public properties get/is">
    /**
     * TODO
     */
    public List<String> getLinearSugars() {
        List<String> tmpSmilesList = new ArrayList<>(this.linearSugars.size());
        SmilesGenerator tmpSmilesGen = new SmilesGenerator(SmiFlavor.Unique);
        for (IAtomContainer tmpLinearSugar : this.linearSugars) {
            String tmpSmiles = null;
            try {
                tmpSmiles = tmpSmilesGen.create(tmpLinearSugar);
            } catch (CDKException aCDKException) {
                SugarRemovalUtility.LOGGER.log(Level.WARNING, aCDKException.toString(), aCDKException);
            }
            tmpSmilesList.add(tmpSmiles);
        }
        return tmpSmilesList;
    }

    /**
     * TODO
     */
    public List<String> getCircularSugars() {
        List<String> tmpSmilesList = new ArrayList<>(this.ringSugars.size());
        SmilesGenerator tmpSmilesGen = new SmilesGenerator(SmiFlavor.Unique);
        for (IAtomContainer tmpRingSugar : this.ringSugars) {
            String tmpSmiles = null;
            try {
                tmpSmiles = tmpSmilesGen.create(tmpRingSugar);
            } catch (CDKException aCDKException) {
                SugarRemovalUtility.LOGGER.log(Level.WARNING, aCDKException.toString(), aCDKException);
            }
            tmpSmilesList.add(tmpSmiles);
        }
        return tmpSmilesList;
    }

    /**
     * TODO
     * @return
     */
    public boolean isGlycosidicBondDetected() {
        return this.detectGlycosidicBond;
    }

    /**
     * TODO
     */
    public boolean areOnlyTerminalSugarsRemoved() {
        return this.removeOnlyTerminal;
    }

    /**
     * TODO
     */
    public StructuresToKeepMode getStructuresToKeepMode() {
        return this.structuresToKeepMode;
    }

    /**
     * TODO
     */
    public int getMwOrHacThreshold() {
        return this.mwOrHacThreshold;
    }

    /**
     * TODO
     */
    public boolean isNrOfAttachedOxygensIncluded() {
        return this.includeNrOfAttachedOxygens;
    }

    /**
     * TODO
     */
    public double getAttachedOxygensToAtomsInRingRatioThreshold() {
        return this.attachedOxygensToAtomsInRingRatioThreshold;
    }

    /**
     * TODO
     */
    public boolean areLinearSugarsInRingsRemoved() {
        return this.removeLinearSugarsInRing;
    }
    //</editor-fold>
    //
    //<editor-fold desc="Public properties set/add/clear">
    /**
     * TODO
     */
    public void addCircularSugar(IAtomContainer aCircularSugar) throws NullPointerException, IllegalArgumentException, IllegalStateException {
        Objects.requireNonNull(aCircularSugar, "Given atom container is 'null'");
        this.ringSugars.add(aCircularSugar);
    }

    /**
     * TODO
     */
    public void addCircularSugar(String aSmilesCode) throws NullPointerException, CDKException, IllegalArgumentException, IllegalStateException {
        Objects.requireNonNull(aSmilesCode, "Given SMILES code is 'null'");
        if (aSmilesCode.isEmpty()) {
            throw new IllegalArgumentException("Given SMILES code is empty");
        }
        SmilesParser tmpSmiPar = new SmilesParser(DefaultChemObjectBuilder.getInstance());
        IAtomContainer tmpRingSugar = tmpSmiPar.parseSmiles(aSmilesCode);
        this.addCircularSugar(tmpRingSugar);
    }

    /**
     * TODO
     */
    public void addLinearSugar(IAtomContainer aLinearSugar) throws NullPointerException, IllegalArgumentException, IllegalStateException {
        Objects.requireNonNull(aLinearSugar, "Given atom container is 'null'");
        this.linearSugars.add(aLinearSugar);
        this.linearSugarPatterns.add(DfPattern.findSubstructure(aLinearSugar));
    }

    /**
     * TODO
     */
    public void addLinearSugar(String aSmilesCode) throws NullPointerException, CDKException, IllegalArgumentException, IllegalStateException {
        Objects.requireNonNull(aSmilesCode, "Given SMILES code is 'null'");
        if (aSmilesCode.isEmpty()) {
            throw new IllegalArgumentException("Given SMILES code is empty");
        }
        SmilesParser tmpSmiPar = new SmilesParser(DefaultChemObjectBuilder.getInstance());
        IAtomContainer tmpLinearSugar = tmpSmiPar.parseSmiles(aSmilesCode);
        this.addLinearSugar(tmpLinearSugar);
    }

    /**
     * TODO
     */
    public void clearCircularSugars() {
        this.ringSugars.clear();
    }

    /**
     * TODO
     */
    public void clearLinearSugars() {
        this.linearSugars.clear();
        this.linearSugarPatterns.clear();
    }

    /**
     * TODO
     */
    public void setDetectGlycosidicBond(boolean aBoolean) {
        this.detectGlycosidicBond = aBoolean;
    }

    /**
     * TODO
     */
    public void setRemoveOnlyTerminalSugars(boolean aBoolean) {
        this.removeOnlyTerminal = aBoolean;
    }

    /**
     * TODO
     */
    public void setStructuresToKeepMode(StructuresToKeepMode aMode) throws NullPointerException {
        Objects.requireNonNull(aMode, "Given mode is 'null'.");
        this.structuresToKeepMode = aMode;
        this.mwOrHacThreshold = this.structuresToKeepMode.getDefaultThreshold();
    }

    /**
     * TODO
     */
    public void setStructuresToKeepThreshold(int aThreshold) throws IllegalArgumentException {
        if ((this.structuresToKeepMode == StructuresToKeepMode.ALL) && (aThreshold > 1)) {
            throw new IllegalArgumentException("The mode is currently set to keep all structures, so a threshold > 1 makes no sense.");
        }
        if (aThreshold < 0) {
            throw new IllegalArgumentException("Threshold cannot be negative.");
        }
        this.mwOrHacThreshold = aThreshold;
    }

    /**
     * TODO
     */
    public void setIncludeNrOfAttachedOxygens(boolean aBoolean) {
        this.includeNrOfAttachedOxygens = aBoolean;
    }

    /**
     * TODO
     */
    public void setAttachedOxygensToAtomsInRingRatioThreshold(double aDouble) throws IllegalArgumentException {
        boolean tmpIsFinite = Double.isFinite(aDouble); //false for NaN and infinity arguments
        boolean tmpIsNegative = (aDouble < 0);
        if(!tmpIsFinite || tmpIsNegative) {
            throw new IllegalArgumentException("Given double is NaN, infinite or negative.");
        }
        boolean tmpIsBiggerThanOne = (aDouble > 1);
        if (tmpIsBiggerThanOne) {
            throw new IllegalArgumentException("Threshold cannot be bigger than one.");
        }
        if ((this.includeNrOfAttachedOxygens == false) && aDouble > 0) {
            throw new IllegalArgumentException("The number of attached oxygen atoms is currently not included in the " +
                    "decision making process, so a ration threshold > 0 makes no sense.");
        }
    }

    /**
     * TODO
     */
    public void setRemoveLinearSugarsInRing(boolean aBoolean) {
        this.removeLinearSugarsInRing = aBoolean;
    }
    //</editor-fold>
    //
    //<editor-fold desc="Public methods">
    /**
     * TODO: Add doc
     *
     * @param aMolecule
     * @return
     * @throws
     */
    public IAtomContainer removeSugars(IAtomContainer aMolecule, boolean aShouldBeCloned) throws NullPointerException {
        Objects.requireNonNull(aMolecule, "Given molecule is 'null'.");
        IAtomContainer tmpNewMolecule;
        try {
            if (aShouldBeCloned) {
                tmpNewMolecule = aMolecule.clone();
            } else {
                tmpNewMolecule = aMolecule;
            }
            //***removing ring sugars***
            int[][] tmpAdjList = GraphUtil.toAdjList(tmpNewMolecule);
            //efficient computation/partitioning of the ring systems
            RingSearch tmpRingSearch = new RingSearch(tmpNewMolecule, tmpAdjList);
            List<IAtomContainer> tmpIsolatedRings = tmpRingSearch.isolatedRingFragments();
            List<IAtomContainer> tmpFusedRings = tmpRingSearch.fusedRingFragments();
            for(IAtomContainer tmpReferenceRing : this.ringSugars){
                for(IAtomContainer tmpIsolatedRing : tmpIsolatedRings){
                    if (this.univIsomorphismTester.isIsomorph(tmpReferenceRing, tmpIsolatedRing)) {
                        //do not remove rings with non-single exocyclic bonds
                        boolean tmpAreAllExocyclicBondsSingle = SugarRemovalUtility.areAllExocyclicBondsSingle(tmpIsolatedRing, tmpNewMolecule);
                        if (!tmpAreAllExocyclicBondsSingle) {
                            continue;
                        }
                        //do not remove rings with 'too few' attached oxygens
                        int tmpExocyclicOxygenCount = SugarRemovalUtility.getAttachedOxygenAtomCount(tmpIsolatedRing, tmpNewMolecule);
                        int tmpAtomsInRing = tmpIsolatedRing.getAtomCount();
                        boolean tmpAreEnoughOxygensAttached = SugarRemovalUtility.doesRingHaveEnoughOxygenAtomsAttached(tmpAtomsInRing, tmpExocyclicOxygenCount);
                        if (!tmpAreEnoughOxygensAttached) {
                            continue;
                        }
                        //TODO: Move to constant
                        tmpNewMolecule.setProperty("CONTAINS_RING_SUGAR", 1);
                        //remove found ring
                        for(IAtom tmpAtom : tmpIsolatedRing.atoms()){
                            if (tmpNewMolecule.contains(tmpAtom)) {
                                tmpNewMolecule.removeAtom(tmpAtom);
                            }
                        }
                        tmpIsolatedRings.remove(tmpIsolatedRing);
                    }
                }
            }
            //***removing linear sugars***
            boolean tmpContainsLinearSugar = false;
            //iterating over linear sugar patterns
            for(DfPattern tmpPattern : this.linearSugarPatterns) {
                int[][] tmpUniqueMappings = tmpPattern.matchAll(tmpNewMolecule).uniqueAtoms().toArray();
                //Linear sugars that are detected as part of a larger ring should not be removed
                //iterating over mappings of one pattern
                for (int[] tmpMapping : tmpUniqueMappings) {
                    boolean tmpIsPartOfRing = false;
                    //iterating over atoms of one mapping
                    for (int i = 0; i < tmpMapping.length; i++) {
                        //TODO: Why this if?
                        if(tmpNewMolecule.getAtomCount() >= i) {
                            //iterating over fused rings to check whether atom is part of a ring
                            for(IAtomContainer tmpFusedRing : tmpFusedRings){
                                if(tmpFusedRing.contains(tmpNewMolecule.getAtom(i))) {
                                    //mapped atom is in a ring
                                    tmpIsPartOfRing = true;
                                    //breaking iteration of rings
                                    break;
                                }
                            }
                            if (tmpIsPartOfRing) {
                                //breaking iteration of atoms in mapping
                                break;
                            }
                            //doing the same for isolated rings
                            for(IAtomContainer tmpIsolatedRing : tmpIsolatedRings) {
                                if(tmpIsolatedRing.contains(tmpNewMolecule.getAtom(i))) {
                                    //mapped atom is in a ring
                                    tmpIsPartOfRing = true;
                                    break;
                                }
                            }
                            if (tmpIsPartOfRing) {
                                break;
                            }
                        }
                    }
                    if(!tmpIsPartOfRing ) {
                        ArrayList<IAtom> tmpAtomsToRemove = new ArrayList<>(tmpMapping.length);
                        for (int i = 0; i < tmpMapping.length; i++) {
                            //TODO: Why this if?
                            if(tmpNewMolecule.getAtomCount() >= i) {
                                try {
                                    IAtom atomToRemove = tmpNewMolecule.getAtom(i);
                                    tmpAtomsToRemove.add(atomToRemove);
                                } catch (IndexOutOfBoundsException anException) {
                                    SugarRemovalUtility.LOGGER.log(Level.SEVERE, anException.toString(), anException);
                                }
                            }
                        }
                        //TODO: To prevent removal of sugars that are not there anymore? Move to constant or option
                        if(tmpAtomsToRemove.size() >= 4) {
                            tmpContainsLinearSugar = true;
                        }
                        for(IAtom tmpAtom : tmpAtomsToRemove) {
                            if (tmpNewMolecule.contains(tmpAtom)) {
                                tmpNewMolecule.removeAtom(tmpAtom);
                            }
                        }
                    }
                }
            }
            //TODO: Move to constant
            if(tmpContainsLinearSugar) {
                tmpNewMolecule.setProperty("CONTAINS_LINEAR_SUGAR", 1);
            }
            //select only the biggest remaining unconnected part of the molecule
            tmpNewMolecule = SugarRemovalUtility.selectBiggestUnconnectedFragment(tmpNewMolecule);
        //TODO: maybe catch some exceptions in between already
        } catch (CloneNotSupportedException | ConcurrentModificationException | IndexOutOfBoundsException | CDKException anException) {
            SugarRemovalUtility.LOGGER.log(Level.SEVERE, anException.toString(), anException);
            return null;
        }
        return tmpNewMolecule;
    }
    //</editor-fold>
    //
    //<editor-fold desc="Public static methods">
    /**
     * TODO: Add doc
     *
     * @param aMolecule
     * @return
     */
    public static IAtomContainer selectBiggestUnconnectedFragment(IAtomContainer aMolecule) throws NullPointerException {
        Objects.requireNonNull(aMolecule, "Given molecule is 'null'.");
        Map<Object, Object> tmpProperties = aMolecule.getProperties();
        IAtomContainerSet tmpUnconnectedFragments = ConnectivityChecker.partitionIntoMolecules(aMolecule);
        if(tmpUnconnectedFragments != null && tmpUnconnectedFragments.getAtomContainerCount() >= 1) {
            IAtomContainer tmpBiggestFragment = tmpUnconnectedFragments.getAtomContainer(0);
            for(IAtomContainer tmpFragment : tmpUnconnectedFragments.atomContainers()){
                if(tmpFragment.getAtomCount() > tmpBiggestFragment.getAtomCount()){
                    tmpBiggestFragment = tmpFragment;
                }
            }
            aMolecule = tmpBiggestFragment;
            int tmpHeavyAtomCount = 0;
            for(IAtom tmpAtom : aMolecule.atoms()){
                if(!tmpAtom.getSymbol().equals("H")){
                    tmpHeavyAtomCount++;
                }
            }
            //Discard 'too small' remaining fragments
            //TODO: Move to constants or rather option
            if(tmpHeavyAtomCount < 5){
                return null;
            }
        } else {
            return null;
        }
        aMolecule.setProperties(tmpProperties);
        return aMolecule;
    }

    /**
     * Checks whether all exocyclic bonds connected to a given ring fragment of an original atom container are of single
     * order.
     * <p>
     *     The method iterates over all cyclic atoms and all of their bonds. So the runtime scales linear with the number
     *     of cyclic atoms and their connected bonds.
     * </p>
     *
     * @param aRingToTest the ring fragment to test; exocyclic bonds do not have to be included in the fragment but if it
     *                    is a fused system of multiple rings, the internal interconnecting bonds of the different rings
     *                    need to be included; all its atoms need to be exactly the same objects as in the second atom
     *                    container parameter
     * @param anOriginalMolecule the molecule that contains the ring under investigation; The exocyclic bonds will be
     *                           queried from it
     * @return true, if all exocyclic bonds connected to the ring are of single order
     * @throws NullPointerException if a parameter is 'null'
     */
    public static boolean areAllExocyclicBondsSingle(IAtomContainer aRingToTest, IAtomContainer anOriginalMolecule) throws NullPointerException {
        Objects.requireNonNull(aRingToTest, "Given ring atom container is 'null'");
        Objects.requireNonNull(anOriginalMolecule, "Given atom container representing the original molecule is 'null'");
        int tmpAtomCountInRing = aRingToTest.getAtomCount();
        int tmpArrayListInitCapacity = tmpAtomCountInRing * 3;
        List<IBond> tmpExocyclicBondsList = new ArrayList<>(tmpArrayListInitCapacity);
        Iterable<IAtom> tmpRingAtoms = aRingToTest.atoms();
        for (IAtom tmpRingAtom : tmpRingAtoms) {
            if (!anOriginalMolecule.contains(tmpRingAtom)) {
                continue;
            }
            //TODO: 'NoSuchAtomException: Atom is not a member of this AtomContainer' is thrown sometimes in the following line
            List<IBond> tmpConnectedBondsList = anOriginalMolecule.getConnectedBondsList(tmpRingAtom);
            for (IBond tmpBond : tmpConnectedBondsList) {
                boolean tmpIsInRing = aRingToTest.contains(tmpBond);
                if (!tmpIsInRing) {
                    tmpExocyclicBondsList.add(tmpBond);
                }
            }
        }
        return (BondManipulator.getMaximumBondOrder(tmpExocyclicBondsList) == IBond.Order.SINGLE);
    }

    //TODO: Also count N and S (or all hetero atoms)?
    /**
     * Gives the number of attached exocyclic oxygen atoms of a given ring fragment of an original atom container.
     * <p>
     *     The method iterates over all cyclic atoms and all of their connected atoms. So the runtime scales linear with the number
     *     of cyclic atoms and their connected atoms.
     * </p>
     *
     * @param aRingToTest the ring fragment to test; exocyclic bonds do not have to be included in the fragment but if it
     *                    is a fused system of multiple rings, the internal interconnecting bonds of the different rings
     *                    need to be included; all its atoms need to be exactly the same objects as in the second atom
     *                    container parameter
     * @param anOriginalMolecule the molecule that contains the ring under investigation; The exocyclic bonds will be
     *                           queried from it
     * @return number of attached exocyclic oxygen atoms
     * @throws NullPointerException if a parameter is 'null'
     */
    public static int getAttachedOxygenAtomCount(IAtomContainer aRingToTest, IAtomContainer anOriginalMolecule) throws NullPointerException {
        Objects.requireNonNull(aRingToTest, "Given ring atom container is 'null'");
        Objects.requireNonNull(anOriginalMolecule, "Given atom container representing the original molecule is 'null'");
        int tmpExocyclicOxygenCounter = 0;
        Iterable<IAtom> tmpRingAtoms = aRingToTest.atoms();
        for (IAtom tmpRingAtom : tmpRingAtoms) {
            if (!anOriginalMolecule.contains(tmpRingAtom)) {
                continue;
            }
            //TODO: 'NoSuchAtomException: Atom is not a member of this AtomContainer' is thrown sometimes in the following line
            List<IAtom> tmpConnectedAtomsList = anOriginalMolecule.getConnectedAtomsList(tmpRingAtom);
            for (IAtom tmpConnectedAtom : tmpConnectedAtomsList) {
                String tmpSymbol = tmpConnectedAtom.getSymbol();
                boolean tmpIsOxygen = tmpSymbol.matches("O");
                boolean tmpIsInRing = aRingToTest.contains(tmpConnectedAtom);
                if (tmpIsOxygen && !tmpIsInRing) {
                    tmpExocyclicOxygenCounter++;
                }
            }
        }
        return tmpExocyclicOxygenCounter;
    }

    /**
     * Simple decision making function for deciding whether a possible sugar ring has enough exocyclic oxygen atom
     * attached to it. This number should be higher or equal to the number of atoms in the ring (including the cyclic
     * oxygen atom) divided by two. So at least 3 attached exocyclic oxygen atoms for a six-membered ring, 2 for a
     * five-membered ring etc. A simple integer division is used.
     *
     * @param aNumberOfAtomsInRing number of atoms in the possible sugar ring, including the cyclic oxygen atom
     * @param aNumberOfAttachedExocyclicOxygenAtoms number of attached exocyclic oxygen atom of the ring under
     *                                              investigation
     * @return true, if the number of attached exocyclic oxygen atoms is at least half of the number of atoms in the ring
     */
    public static boolean doesRingHaveEnoughOxygenAtomsAttached(int aNumberOfAtomsInRing, int aNumberOfAttachedExocyclicOxygenAtoms) {
        return (aNumberOfAttachedExocyclicOxygenAtoms >= (aNumberOfAtomsInRing / 2));
    }
    //</editor-fold>
}
