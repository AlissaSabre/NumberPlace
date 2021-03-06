package com.gmail.at.sabre.alissa.numberplace.solver;

import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.ISolverService;
import org.sat4j.specs.TimeoutException;

/***
 * Solve a number place puzzle using a SAT solver.
 *
 * @author alissa
 */
public class PuzzleSolver {

	/***
	 * The dimension of the basic block.
	 * It is 3 for a standard Number Place puzzle.
	 */
	private static final int N = 3;

	/***
	 * The dimension (i.e., both the height and the width) of the whole puzzle.
	 * It is 9 for a standard Number Place puzzle.
	 */
	private static final int NN = N * N;

    private volatile boolean mIsStopped;

    private ISolver mSolver;

    // public methods

    /***
     * Solve a number place puzzle. This method may block for some long time
     * (e.g., several seconds.) UI thread should not invoke it.
     *
     * @param puzzle
     *            The puzzle to solve.
     * @return
     *            A solution, or null if no solution was found.
     */
    public synchronized byte[][] solve(final byte[][] puzzle) {

        try {

            mIsStopped = false;

            mSolver = SolverFactory.newDefault();
            mSolver.newVar(MAXVAR);

            addPuzzleRules();
            addInstanceConstraints(puzzle);

            if (mSolver.isSatisfiable()) {
                return extractSolution();
            }

        } catch (ContradictionException e) {
        } catch (TimeoutException e) {
        } finally {
            // Free expensive resources.
            mSolver = null;
        }

        return null;
    }

    /***
     * Stop solving the puzzle. This method in intended to be invoked by a UI
     * thread when another thread (background worker thread) is executing
     * {@link #solve(byte[][])}. The execution in the worker thread soon stops
     * and the method {@link #solve(byte[][])} returns null. This method itself
     * returns very soon.
     */
    public void stop() {
        mIsStopped = true;
        final ISolver solver = mSolver;
        if (solver != null) {
            ((ISolverService)solver).stop();
        }
    }

    /***
     * Indicates whether the recent invocation of {@link #solve(byte[][])} has
     * stopped by {@link #stop()}.
     *
     * @return true if stopped.
     */
    public boolean isStopped() {
        return mIsStopped;
    }

    // Constraints

    /***
     * Add several clauses to the SAT solver to define the rules of number
     * place. The clauses added by this method are independent from a number
     * place puzzle instance.
     *
     * @throws ContradictionException
     *             Never thrown unless this method has a serious bug.
     */
    private void addPuzzleRules() throws ContradictionException {

        // Each cell contains a digit.
        for (int y = 0; y < NN; y++) {
            for (int x = 0; x < NN; x++) {
                final VecInt v = new VecInt(NN);
                for (int d = 1; d <= NN; d++) {
                    v.push(var(x, y, d));
                }
                mSolver.addClause(v);
            }
        }

        // No two digits share a same cell.
        // (Each cell contains only one digit.)
        for (int d1 = 1; d1 <= NN; d1++) {
            for (int d2 = d1 + 1; d2 <= NN; d2++) {
                for (int y = 0; y < NN; y++) {
                    for (int x = 0; x < NN; x++) {
                        final VecInt v = new VecInt(2);
                        v.push(-var(x, y, d1));
                        v.push(-var(x, y, d2));
                        mSolver.addClause(v);
                    }
                }
            }
        }

        // No two cells in a row contain a same digit.
        for (int y = 0; y < NN; y++) {
            for (int x1 = 0; x1 < NN; x1++) {
                for (int x2 = x1 + 1; x2 < NN; x2++) {
                    for (int d = 1; d <= NN; d++) {
                        final VecInt v = new VecInt(2);
                        v.push(-var(x1, y, d));
                        v.push(-var(x2, y, d));
                        mSolver.addClause(v);
                    }
                }
            }
        }

        // No two cells in a column contain a same digit.
        for (int x = 0; x < NN; x++) {
            for (int y1 = 0; y1 < NN; y1++) {
                for (int y2 = y1 + 1; y2 < NN; y2++) {
                    for (int d = 1; d <= NN; d++) {
                        final VecInt v = new VecInt(2);
                        v.push(-var(x, y1, d));
                        v.push(-var(x, y2, d));
                        mSolver.addClause(v);
                    }
                }
            }
        }

        // No two cells in a 3x3 block contain a same digit.
        for (int y0 = 0; y0 < NN; y0 += N) {
            for (int x0 = 0; x0 < NN; x0 += N) {
                for (int p1 = 0; p1 < NN; p1++) {
                    final int x1 = x0 + p1 % N;
                    final int y1 = y0 + p1 / N;
                    for (int p2 = p1 + 1; p2 < NN; p2++) {
                        final int x2 = x0 + p2 % N;
                        final int y2 = y0 + p2 / N;
                        for (int d = 1; d <= NN; d++) {
                            final VecInt v = new VecInt(2);
                            v.push(-var(x1, y1, d));
                            v.push(-var(x2, y2, d));
                            mSolver.addClause(v);
                        }
                    }
                }
            }
        }
    }

    /***
     * Add a set of clauses to the SAT solver to represent a number place puzzle
     * instance.
     *
     * @param puzzle
     *            The puzzle instance.
     * @throws ContradictionException
     *             When the puzzle included a trivial contradiction.
     */
    private void addInstanceConstraints(final byte[][] puzzle) throws ContradictionException {
        for (int y = 0; y < NN; y++) {
            for (int x = 0; x < NN; x++) {
                final int d = puzzle[y][x];
                if (d > 0) {
                    final VecInt v = new VecInt(1);
                    v.push(var(x, y, d));
                    mSolver.addClause(v);
                }
            }
        }
    }

    // Handling results.

    /***
     * Extract a solution in a number place puzzle format. Note that this method
     * invokes {@link ISolver#model()} method immediately, so
     * {@link ISolver#isSatisfiable()} method should have been issued
     * previously.
     *
     * @return The extracted solution.
     */
    private byte[][] extractSolution() {
        int[] model = mSolver.model();
        byte[][] solution = new byte[NN][NN];

        for (int i = 0; i < model.length; i++) {
            final int id = model[i];
            if (id > 0) {
                final int x = varX(id);
                final int y = varY(id);
                final int d = varD(id);
                solution[y][x] = (byte)d;
            }
        }

        return solution;
    }

    // Variable encoding/decoding.

    /***
     * The number of SAT4J variables we need to represent a number place puzzle.
     */
    private static final int MAXVAR = NN * NN * NN + 1; // XXX: Do we really need '+1' here?

    /***
     * Find a SAT4J (Dimacs) variable id (number) to represent a digit in a
     * cell. If the cell at (x,y) holds the digit d, the variable of id var(x,
     * y, d) is true.
     *
     * @param x
     *            A value in range 0..8 to represent X position of a cell.
     * @param y
     *            A value in range 0..8 to represent Y position of a cell.
     * @param d
     *            A value in range 1..9 to represent a digit in a cell.
     * @return The variable id.
     */
    private static int var(int x, int y, int d) {
        return x * NN + y * NN * NN + d;
    }

    /***
     * Find and return the X position of the cell that a variable represents.
     *
     * @param id
     *            A variable id. This must be a positive value.
     * @return The X position in range 0..8.
     */
    private static int varX(int id) {
        return (id - 1) / NN % NN;
    }

    /***
     * Find and return the Y position of the cell that a variable represents.
     *
     * @param id
     *            A variable id. This must be a positive value.
     * @return The Y position in range 0..8.
     */
    private static int varY(int id) {
        return (id - 1) / (NN * NN);
    }

    /***
     * Find and return the digit that a variable represents.
     *
     * @param id
     *            A variable id. This must be a positive value.
     * @return The digit in range 1..9.
     */
    private static int varD(int id) {
        return (id - 1) % NN + 1;
    }
}
