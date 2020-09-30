package g4;

import java.util.*;

import sim.Game;
import sim.GameHistory;
import sim.SimPrinter;

public class Player extends sim.Player {

	private int goalBank;
	private final MovePredictor movePredictor;
	private int threshold;
	private SimPrinter simPrinter;
	private int teamId;

	/**
	 * Player constructor
	 *
	 * @param teamID     team ID
	 * @param rounds     number of rounds
	 * @param seed       random seed
	 * @param simPrinter simulation printer
	 *
	 */
	public Player(Integer teamID, Integer rounds, Integer seed, SimPrinter simPrinter) {
		super(teamID, rounds, seed, simPrinter);
		this.simPrinter = simPrinter;
		this.goalBank = 0;
		this.movePredictor = new MovePredictor(simPrinter);
		this.threshold = 3;
		this.teamId = teamID;
	}

	/**
	 * Reallocate player goals
	 *
	 * @param round            current round
	 * @param gameHistory      cumulative game history from all previous rounds
	 * @param playerGames      state of player games before reallocation
	 * @param opponentGamesMap state of opponent games before reallocation (map of
	 *                         opponent team IDs to their games)
	 * @return state of player games after reallocation
	 *
	 */
	public List<Game> reallocate(Integer round, GameHistory gameHistory, List<Game> playerGames,
			Map<Integer, List<Game>> opponentGamesMap) {
		List<Game> reallocatedPlayerGames = new ArrayList<>();

		if (round == 1)
		{
			reallocatedPlayerGames = roundOneReallocate(round, gameHistory, playerGames, opponentGamesMap);
		}
		else
		{
			//System.out.println(gameHistory.getAllAverageRankingsMap().get(round - 1));
			Map<Integer, Double> currentAverages = gameHistory.getAllAverageRankingsMap().get(round - 1);
			double g4MeanRanking = currentAverages.get(teamId);

			LinkedHashMap<Integer, Double> sortedRanks = new LinkedHashMap<>();
			
			currentAverages.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByValue())
				.forEachOrdered(x -> sortedRanks.put(x.getKey(), x.getValue()));
			
			List<Double> rankList = new ArrayList<Double>(sortedRanks.values());
//			System.out.println(rankList.toString());
			// pointPredictor.trackData(opponentGamesMap);

			// List<Game> reallocatedPlayerGames = new ArrayList<>();

			// List<Game> wonGames = getWinningGames(playerGames);
			// List<Game> drawnGames = getDrawnGames(playerGames);
			// List<Game> lostGames = getLosingGames(playerGames);
			if (1 < rankList.indexOf(g4MeanRanking) && rankList.indexOf(g4MeanRanking) < 5) {
				reallocatedPlayerGames = attackHigherRanks(round, gameHistory, playerGames, opponentGamesMap);
//				System.out.println("HERE2");

			}
			else {
//				System.out.println("HERE2");
				reallocatedPlayerGames = reallocateLeapFrog(round, gameHistory, playerGames, opponentGamesMap);
			}
		}
		//List<Game> leapfrogGames = reallocateLeapFrog(round, gameHistory, playerGames, opponentGamesMap);
		//List<Game> attackHigherRankGames = attackHigherRanks(round, gameHistory, playerGames, opponentGamesMap);

		// System.out.println("Reallocating goals");
		//calculateBank(wonGames, targetTeamID.get(0), targetTeamID.get(1));
		//this.movePredictor.trackData(opponentGamesMap);
		// System.out.println(this.goalBank);
		// System.out.println("Bank:" + goalBank);
		//transferGoalsToLostGames(lostGames);
		//transferGoalsToDrawnGames(drawnGames);

		// adjust winning games score
		/*
		int goalsTakenFromWins = 0;
		for (Game winningGame : wonGames) {
			goalsTakenFromWins += winningGame.getNumPlayerGoals() - winningGame.getNumOpponentGoals() - 1;
			winningGame.setNumPlayerGoals(winningGame.getNumOpponentGoals() + 1);
		}
		// System.out.println("Goals taken:" + goalsTakenFromWins);

		reallocatedPlayerGames.addAll(lostGames);
		reallocatedPlayerGames.addAll(drawnGames);
		reallocatedPlayerGames.addAll(wonGames);
		this.goalBank = 0;
		// check constraints and return
		if (checkConstraintsSatisfied(playerGames, reallocatedPlayerGames))
			return reallocatedPlayerGames;

		return playerGames; */
		//return leapfrogGames;
		return reallocatedPlayerGames;
	}

	public List<Game> reallocateLeapFrog(Integer round, GameHistory gameHistory, List<Game> playerGames,
			Map<Integer, List<Game>> opponentGamesMap) {

		Map<Integer, Double> currentAverages = gameHistory.getAllAverageRankingsMap().get(round - 1);
		List<Integer> nearestTeams = sortGamesByAverageRanking(currentAverages);
		List<Game> playerGamesClone = new ArrayList<>();
		playerGamesClone.addAll(playerGames);
		double g4MeanRanking = currentAverages.get(teamId);
		nearestTeams.remove(0);
		List<Game> wonGames = getWinningGames(playerGames);
		// for (Integer team : nearestTeams) {
		// 	for(Game game: playerGames){
		// 		if(wonGames.contains(game)){
		// 			gameIDGoalReserviorException.add(game.getID());
		// 		} 
		// 	}
		// }
		for (Game wonGame : wonGames){
			if(!nearestTeams.contains(wonGame.getID())){
				int numPlayerGoals = wonGame.getNumPlayerGoals();
				int numOpponentGoals = wonGame.getNumOpponentGoals();
				this.goalBank += numPlayerGoals - numOpponentGoals - 1;
			}
		}
		for(Integer targetTeam: nearestTeams){
			Game currentGame = getGameFromOpponentID(targetTeam, opponentGamesMap, playerGames, round);
			
			int lostBy = currentGame.getNumOpponentGoals() - currentGame.getNumPlayerGoals();
			int goalsToAdd = lostBy + 1;
			if (goalBank >= goalsToAdd) {
				currentGame = transferFromBank(currentGame, goalsToAdd);
				this.goalBank -= goalsToAdd;
			} else {
				currentGame = transferFromBank(currentGame, goalBank);
				this.goalBank = 0;
			}
			for(Game played: playerGames){
				if(played.getID().equals(currentGame.getID())){
					playerGames.set(playerGames.indexOf(played), currentGame);
				}
			}
		}
		return playerGames;
	}

	public Game getGameFromOpponentID(int targetTeam, Map<Integer, List<Game>> opponentGamesMap, List<Game> playerGames, Integer round){
		for(Game opponentGame: playerGames){
			if(targetTeam == (opponentGame.getID())){
				return opponentGame;
			}
		}
		return null;
	}

	public List<Integer> sortGamesByAverageRanking(Map<Integer, Double> currentRankingAverages) {
		Double g4Ranking = currentRankingAverages.get(this.teamID);
//		Map<Integer, Double> unsortedMap = new HashMap<>(currentRankingAverages);
		LinkedHashMap<Integer, Double> sortedMap = new LinkedHashMap<>();
		sortedMap.remove(this.teamID);
		Set<Integer> keys = sortedMap.keySet();

		for (Integer key: keys){
			double relativeMeanRanking = currentRankingAverages.get(key);
			relativeMeanRanking -= g4Ranking;
			currentRankingAverages.replace(key, Math.abs(relativeMeanRanking));
		}

		currentRankingAverages.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByValue())
			.forEachOrdered(team -> sortedMap.put(team.getKey(), team.getValue()));

		return new ArrayList<Integer>(sortedMap.keySet());
	}

	// algorithm #2:
	// induce losses for higher ranked teams
	// risk losing to teams that are lower ranked
	// don't use this algorithm when player is rank 1
	// TODO: figure out the highest rank at which you would want to use this
	// algorithm
	// TODO: figure out how many teams above you want to attack (1 at the moment)
	// TODO: figure out how many teams below you are willing to lose against (1 at
	// the moment)
	public List<Game> attackHigherRanks(Integer round, GameHistory gameHistory, List<Game> playerGames,
			Map<Integer, List<Game>> opponentGamesMap) {
		List<Game> reallocatedPlayerGames = new ArrayList<>();
		List<Game> wonGames = getWinningGames(playerGames);
		List<Game> drawnGames = getDrawnGames(playerGames);
		List<Game> lostGames = getLosingGames(playerGames);

		Double highestRank = Double.MIN_VALUE;
		Double lowestRank = Double.MAX_VALUE;
		int highestRankTeam = 0;
		int lowestRankTeam = 0;
		Map<Integer, Double> currentAverages = gameHistory.getAllAverageRankingsMap().get(round - 1);
		List<Double> highRankedTeams = new ArrayList<Double>();
		List<Double> lowRankedTeams = new ArrayList<Double>();
		double playerRank = currentAverages.get(teamId);
		int goalsTakenFromLowest = 0;
		int numGoalsToReallocate = 0;

		for (Map.Entry<Integer, Double> entry : currentAverages.entrySet()) {
			if (entry.getValue() < playerRank) {
				if (lowestRank > entry.getValue() && teamId != entry.getKey()) {
					lowestRankTeam = entry.getKey();
				}
			} else {
				if (highestRank < entry.getValue() && teamId != entry.getKey()) {
					highestRankTeam = entry.getKey();
				}
			}
		}

		Game lowestRankGame = getGameFromOpponentID(lowestRankTeam, opponentGamesMap, playerGames, round);
		Game highestRankGame = getGameFromOpponentID(highestRankTeam, opponentGamesMap, playerGames, round);

		for (Game winningGame : wonGames) {
			if (lowestRankGame.getID() == winningGame.getID()) {
				numGoalsToReallocate += winningGame.getHalfNumPlayerGoals();
				goalsTakenFromLowest += winningGame.getHalfNumPlayerGoals();
				winningGame.setNumPlayerGoals(winningGame.getNumPlayerGoals() - winningGame.getHalfNumPlayerGoals());
			}
			else {
				numGoalsToReallocate += winningGame.getNumPlayerGoals() - winningGame.getNumOpponentGoals() - 1;
				winningGame.setNumPlayerGoals(winningGame.getNumOpponentGoals() + 1);
			}
		}
		for (Game losingGame : lostGames) {
			if (highestRankGame.getID() == losingGame.getID()) {
				losingGame.setNumPlayerGoals(losingGame.getNumPlayerGoals() + goalsTakenFromLowest);
				numGoalsToReallocate -= goalsTakenFromLowest;
			}
			else {
				int lostBy = losingGame.getNumOpponentGoals() - losingGame.getNumPlayerGoals();
				int goalsToAdd = lostBy + 1;
				if (numGoalsToReallocate >= goalsToAdd) {
					losingGame.setNumPlayerGoals(losingGame.getNumPlayerGoals() + goalsToAdd);
					numGoalsToReallocate -= goalsToAdd;
				} else {
					losingGame.setNumPlayerGoals(losingGame.getNumPlayerGoals() + numGoalsToReallocate);
					numGoalsToReallocate = 0;
				}
			}
		}
		for (Game drawnGame : drawnGames) {
			if (this.goalBank > 0) {
				transferFromBank(drawnGame, 1);
				this.goalBank--;
			}
		}
		reallocatedPlayerGames.addAll(lostGames);
		reallocatedPlayerGames.addAll(drawnGames);
		reallocatedPlayerGames.addAll(wonGames);
		// check constraints and return
		// if (checkConstraintsSatisfied(playerGames, reallocatedPlayerGames)) {
		// 	System.out.println("HERE2");
		// 	return reallocatedPlayerGames;
		// }
		System.out.println("HERE");
		return playerGames;
	}

	public List<Game> roundOneReallocate(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap) {
		List<Game> reallocatedPlayerGames = new ArrayList<>();
		
		List<Game> wonGames = getWinningGames(playerGames);
		List<Game> drawnGames = getDrawnGames(playerGames);
		List<Game> lostGames = getLosingGames(playerGames);
	   
		int goalsTakenFromWins = 0;
		for (Game winningGame : wonGames) {
			goalsTakenFromWins += 1;
			winningGame.setNumPlayerGoals(winningGame.getNumPlayerGoals() - 1);
		}

		for (Game drawingGame : drawnGames) {
			if (goalsTakenFromWins == 1) {
				break;
			}
			else {
				drawingGame.setNumPlayerGoals(drawingGame.getNumPlayerGoals() + 1);
			}
		}
		
		reallocatedPlayerGames.addAll(wonGames);
		reallocatedPlayerGames.addAll(drawnGames);
		reallocatedPlayerGames.addAll(lostGames);

		if(checkConstraintsSatisfied(playerGames, reallocatedPlayerGames))
			return reallocatedPlayerGames;
		return playerGames;
	}

	/**
	 * Calculates Goal Bank
	 *
	 * @param playerGames state of player games before reallocation
	 *
	 */
	private void calculateBank(List<Game> playerGames, int target1, int target2) {
		for (Game game : playerGames) {
			int numPlayerGoals = game.getNumPlayerGoals();
			int numOpponentGoals = game.getNumOpponentGoals();
			this.goalBank += numPlayerGoals - numOpponentGoals - 1;
			// System.out.println(game.getID() + ": " + this.goalBank + " " + numPlayerGoals
			// + " " + numOpponentGoals);

		}
	}

	private List<Game> getWinningGames(List<Game> playerGames) {
		List<Game> winningGames = new ArrayList<>();
		for (Game game : playerGames) {
			int numPlayerGoals = game.getNumPlayerGoals();
			int numOpponentGoals = game.getNumOpponentGoals();
			if (numPlayerGoals > numOpponentGoals)
				winningGames.add(game.cloneGame());
		}
		return winningGames;
	}

	private List<Game> getDrawnGames(List<Game> playerGames) {
		List<Game> drawnGames = new ArrayList<>();
		for (Game game : playerGames) {
			int numPlayerGoals = game.getNumPlayerGoals();
			int numOpponentGoals = game.getNumOpponentGoals();
			if (numPlayerGoals == numOpponentGoals)
				drawnGames.add(game.cloneGame());
		}
		return drawnGames;
	}

	private List<Game> getLosingGames(List<Game> playerGames) {
		List<Game> losingGames = new ArrayList<>();
		for (Game game : playerGames) {
			int numPlayerGoals = game.getNumPlayerGoals();
			int numOpponentGoals = game.getNumOpponentGoals();
			if (numPlayerGoals < numOpponentGoals)
				losingGames.add(game.cloneGame());
		}
		return losingGames;
	}

	// add points from goalBank to lost games
	private void transferGoalsToLostGames(List<Game> lostGames) {
		sortGamesByAmountWon(lostGames);
		for (Game game : lostGames) {
			int lostBy = game.getNumOpponentGoals() - game.getNumPlayerGoals();
			int goalsToAdd = lostBy + 1;
			if (goalBank >= goalsToAdd) {
				transferFromBank(game, goalsToAdd);
				this.goalBank -= goalsToAdd;
			} else {
				transferFromBank(game, goalBank);
				this.goalBank = 0;
			}
		}
	}

	// add 1 point from goalBank to drawn games
	private void transferGoalsToDrawnGames(List<Game> drawnGames) {
		for (Game game : drawnGames) {
			if (goalBank > 0) {
				transferFromBank(game, 1);
				this.goalBank--;
			}
		}
	}

	/*
	 * sort games by amount won (decreasing) ex: [5, 3, 2, 2, 0, 0, -1, -4]
	 */
	private void sortGamesByAmountWon(List<Game> games) {
		games.sort((g1, g2) -> {
			int g1Diff = g1.getNumPlayerGoals() - g1.getNumOpponentGoals();
			int g2Diff = g2.getNumPlayerGoals() - g2.getNumOpponentGoals();
			return g2Diff - g1Diff;
		});
	}

	private Game transferFromBank(Game game, int goals) {
		goalBank -= goals;
		int currentGoals = game.getNumPlayerGoals();
		game.setNumPlayerGoals(currentGoals + goals);
		return game;
	}

	// only used for internal testing
	private void printGameList(List<Game> games) {
		for (Game game : games) {
			simPrinter.print(game.getID().toString() + ": <= GAME ID ");
			simPrinter.print("player: " + game.getNumPlayerGoals().toString());
			simPrinter.print(", opp: " + game.getNumOpponentGoals().toString());
			simPrinter.println();
		}
	}
}