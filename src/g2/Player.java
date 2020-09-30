package g2;


import java.util.*;

import g2.MultipleLinearRegression;
import sim.Game;
import sim.GameHistory;
import sim.SimPrinter;

public class Player extends sim.Player {
	/**
	* Player constructor
	*
	* @param teamID      team ID
	* @param rounds      number of rounds
	* @param seed        random seed
	* @param simPrinter  simulation printer
	*
	*/
	public Player(Integer teamID, Integer rounds, Integer seed, SimPrinter simPrinter) {
		super(teamID, rounds, seed, simPrinter);
	}

	/**
	* Reallocate player goals
	*
	* @param round             current round
	* @param gameHistory       cumulative game history from all previous rounds
	* @param playerGames       state of player games before reallocation
	* @param opponentGamesMap  state of opponent games before reallocation (map of opponent team IDs to their games)
	* @return                  state of player games after reallocation
	*
	*/
	public List<Game> reallocate(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap) {

		if(round < 25) return treeReallocation(round, gameHistory, playerGames, opponentGamesMap);
		if(round > 100) return rankReallocation(round, gameHistory, playerGames, opponentGamesMap);
			return regressionReallocation(round, gameHistory, playerGames, opponentGamesMap);
	}

	private List<Game> rankReallocation(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap) {
		List<Game> reallocatedPlayerGames = new ArrayList<>();

		Map<Integer, Double> rankedMap = new HashMap<Integer, Double>();
		Map<Integer, String> rankedMapS = new HashMap<Integer, String>();

		List<Game> wonGames = getWinningGames(playerGames);
		List<Game> drawnGames = getDrawnGames(playerGames);
		List<Game> lostGames = getLosingGames(playerGames);

		List<Game> lostOrDrawnGamesWithReallocationCapacity = new ArrayList<>(lostGames);
		lostOrDrawnGamesWithReallocationCapacity.addAll(drawnGames);

		List<Integer> targetTeamList = new ArrayList<>();
		if(round > 2)
			targetTeamList = ConstraintExploit.targetScan(round, this.teamID, gameHistory);
		simPrinter.println(targetTeamList);

		for(Game lostGame : lostGames) {
			if (lostGame.maxPlayerGoalsReached()) {
				lostOrDrawnGamesWithReallocationCapacity.remove(lostGame);
			}
		}
		for(Game drawnGame : drawnGames) {
			if (drawnGame.maxPlayerGoalsReached()) {
				lostOrDrawnGamesWithReallocationCapacity.remove(drawnGame);
			}
		}

		if(!gameHistory.getAllGamesMap().isEmpty() && !gameHistory.getAllAverageRankingsMap().isEmpty()) {
			List<Double> averageRank = new ArrayList<Double>(gameHistory.getAllAverageRankingsMap().get(round-1).values());
			for(int i = 0; i < 9; i++) {
				int opoID = i;
				if(i >= teamID) opoID = opoID + 1;
				Double opoRank = averageRank.get(opoID);
				Double ourRank = averageRank.get(teamID);
				rankedMap.put(gameHistory.getAllGamesMap().get(round - 1).get(teamID).get(i).getID(),(Math.abs(ourRank-opoRank)));
				rankedMapS.put(gameHistory.getAllGamesMap().get(round - 1).get(teamID).get(i).getID(),gameHistory.getAllGamesMap().get(round - 1).get(teamID).get(i).getScoreAsString());
			}
		}

		Comparator<Game> rangeComparatorWon = (Game g1, Game g2) ->
		{return (g1.getNumPlayerGoals()-g1.getNumOpponentGoals()) - (g2.getNumPlayerGoals()-g2.getNumOpponentGoals());};
		Comparator<Game> rangeComparatorRank = (Game g1, Game g2) ->
		{return (int) Math.round((rankedMap.get(g1.getID()) - rankedMap.get(g2.getID()))*1000);};

		Collections.sort(wonGames, rangeComparatorWon.reversed());

		Collections.sort(lostOrDrawnGamesWithReallocationCapacity, rangeComparatorRank.reversed());

		int i = 0;
		for(Game lossOrDrew : lostOrDrawnGamesWithReallocationCapacity) {
			int rangeWon = Math.min((wonGames.get(i).getNumPlayerGoals()-wonGames.get(i).getNumOpponentGoals()),
				wonGames.get(i).getHalfNumPlayerGoals());
			int rangeLD = lossOrDrew.getNumOpponentGoals() - lossOrDrew.getNumPlayerGoals();
			if(rangeLD < rangeWon && lossOrDrew.getNumPlayerGoals() + rangeLD + 1 <= 8) {
				lossOrDrew.setNumPlayerGoals(lossOrDrew.getNumPlayerGoals() + rangeLD + 1);
				wonGames.get(i).setNumPlayerGoals(wonGames.get(i).getNumPlayerGoals() - rangeLD - 1);
				i += 1;
			}
		}

		Collections.sort(lostOrDrawnGamesWithReallocationCapacity, rangeComparatorWon);
		Collections.sort(wonGames, rangeComparatorWon.reversed());

		i = 0;
		for(Game lossOrDrew : lostOrDrawnGamesWithReallocationCapacity) {
			int rangeWon = Math.min((wonGames.get(i).getNumPlayerGoals()-wonGames.get(i).getNumOpponentGoals()),
				wonGames.get(i).getHalfNumPlayerGoals());
			int rangeLD = lossOrDrew.getNumOpponentGoals() - lossOrDrew.getNumPlayerGoals();
			if(rangeLD < rangeWon && lossOrDrew.getNumPlayerGoals() + rangeLD + 1 <= 8) {
				lossOrDrew.setNumPlayerGoals(lossOrDrew.getNumPlayerGoals() + rangeLD + 1);
				wonGames.get(i).setNumPlayerGoals(wonGames.get(i).getNumPlayerGoals() - rangeLD - 1);
				i += 1;
			}
		}

		reallocatedPlayerGames.addAll(wonGames);
		reallocatedPlayerGames.addAll(drawnGames);
		reallocatedPlayerGames.addAll(lostGames);

		List<Game> finalGames = ConstraintExploit.targetedReallocation(targetTeamList, reallocatedPlayerGames);

        for (Game game : playerGames)
            simPrinter.println("OrigGame " + game.getID() + ": " +game.getScoreAsString());

        for (Game game : reallocatedPlayerGames)
            simPrinter.println("rankReallocation " + game.getID() + ": " +game.getScoreAsString());

        for (Game game : finalGames)
            simPrinter.println("ExploitGame " + game.getID() + ": " +game.getScoreAsString());

        simPrinter.println("EXPLOIT (RANKED) CONSTRAINTS TEST: " + checkConstraintsSatisfiedTest(playerGames, finalGames));
        simPrinter.println("RANKED CONSTRAINTS TEST: " + checkConstraintsSatisfiedTest(playerGames, reallocatedPlayerGames));

        if(!targetTeamList.isEmpty())
        	if(checkConstraintsSatisfied(playerGames, finalGames)){
               simPrinter.println("TARGETED (UNDER RANKED) RETURN");
               return finalGames;
           }
        else if(checkConstraintsSatisfied(playerGames, reallocatedPlayerGames)){
        	simPrinter.println("RANKED RETURN");
        	return reallocatedPlayerGames;
        	
        }
        simPrinter.println("Returning RANDOM");
        finalGames = randomReallocate(playerGames);
        return finalGames;
	}

	private List<Game> treeReallocation(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap) {

 
        List<Game> drawOf2 = new ArrayList<>();
        List<Game> drawOf3 = new ArrayList<>();
        List<Game> reallocatedPlayerGames = new ArrayList<>();
        List<Game> wonGames = getWinningGames(playerGames);
        List<Game> drawnGames = getDrawnGames(playerGames);
        List<Game> lostGames = getLosingGames(playerGames);
        

        Map<Integer, Double> rankedMap = new HashMap<Integer, Double>();
        Map<Integer, String> rankedMapS = new HashMap<Integer, String>();
        Map<Integer, Integer> goalsTaken = new HashMap<Integer, Integer>();

        
        int lossUnder3 = 2;
        int lossUnder6 = 3;
        int lossUnder8 = 2;
        int goalsToReallocate = 0;

        List<Integer> targetTeamList = new ArrayList<>();
		if(round > 2)
			targetTeamList = ConstraintExploit.targetScan(round, this.teamID, gameHistory);
		simPrinter.println(targetTeamList);

        for (Game draw : drawnGames) {

          int alloc = getAllocationCapacity(gameHistory, round, draw.getID());

            if (alloc < 3){
              drawOf2.add(draw);
            }
            else if (alloc < 4){
              drawOf3.add(draw);
            }
            else {
              wonGames.add(draw);
            }
        }

        Comparator<Game> rangeComparatorLoss = (Game g1, Game g2) ->
        {return (g1.getNumOpponentGoals()-g1.getNumPlayerGoals()) - (g2.getNumOpponentGoals()-g2.getNumPlayerGoals());};
        Comparator<Game> rangeComparatorDraw = (Game g1, Game g2) ->
        {return g1.getNumOpponentGoals() - g2.getNumOpponentGoals();};


        if (lostGames.size() > 1){
          Collections.sort(lostGames, rangeComparatorLoss);
        }  

        int pos = 0;


        // loss under 3
        //System.out.println("loss size: " + lostGames.size());
        for(Game loss: lostGames){
          //System.out.println(getMargin(loss));
          //System.out.println(loss.getScoreAsString());
          if(getMargin(loss) > 3){
            break;
          }
          else {
            //System.out.println(loss.getScoreAsString());
            int needed = Math.min(lossUnder3, (Game.getMaxGoalThreshold() - loss.getNumPlayerGoals()));
            //System.out.println("LossesUnder3 " + lossUnder3 + " needed " + needed);
            //System.out.println(goalsToReallocate);
            if (goalsToReallocate < needed){
              while(pos<wonGames.size()){

                Game takeFrom = wonGames.get(pos);

                if(takeFrom.getNumPlayerGoals() > takeFrom.getNumOpponentGoals()){
                  int winMargin = getMargin(takeFrom);
                  int alloc = getAllocationCapacity(gameHistory, round, takeFrom.getID());

                  //System.out.println("Pos " + pos + " winMargin " + winMargin + " alloc " + alloc);


                  if (alloc > winMargin) {
                    int goals = takeFrom.getHalfNumPlayerGoals();
                    //System.out.println(goals);
                    goalsToReallocate += goals;
                    takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                  }
                  else {
                    int goals = Math.min(takeFrom.getHalfNumPlayerGoals(), takeFrom.getNumPlayerGoals() - (takeFrom.getNumOpponentGoals() + alloc +1));
                    goals = Math.max(0,goals);
                    //System.out.println(goals);
                    takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                    goalsToReallocate += goals;
                  }
                }
                else {
                  //System.out.println("Pos " + pos + " draw " + takeFrom.getNumPlayerGoals() );
                  int goals = takeFrom.getHalfNumPlayerGoals();
                  //System.out.println(goals);
                  takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                  goalsToReallocate += goals;
                }

                //System.out.println(goalsToReallocate);

                pos++;

                if(goalsToReallocate >= needed){
                  break;
                }
              }

              if (goalsToReallocate >= needed){
                loss.setNumPlayerGoals(loss.getNumPlayerGoals() + needed);
                goalsToReallocate -= needed;
              }
              else{
                loss.setNumPlayerGoals(loss.getNumPlayerGoals() + goalsToReallocate);
                goalsToReallocate = 0;
              }
            }
            else {
              loss.setNumPlayerGoals(loss.getNumPlayerGoals() + needed);
              goalsToReallocate -= needed;
            }
          }

          //System.out.println(loss.getScoreAsString());
        }


        if (drawOf2.size() > 1)
          Collections.sort(drawOf2, rangeComparatorDraw);
         

        // draw under 2
        for(Game draw: drawOf2){
         //System.out.println(draw.getScoreAsString());
          int needed = Math.min(lossUnder3, (Game.getMaxGoalThreshold() - draw.getNumPlayerGoals()));
          //System.out.println("LossesUnder3 " + lossUnder3 + " needed " + needed);
          //System.out.println(goalsToReallocate);
          if (goalsToReallocate < needed){
            while(pos<wonGames.size()){

              Game takeFrom = wonGames.get(pos);

              if(takeFrom.getNumPlayerGoals() > takeFrom.getNumOpponentGoals()){
                int winMargin = getMargin(takeFrom);
                int alloc = getAllocationCapacity(gameHistory, round, takeFrom.getID());

                //System.out.println("Pos " + pos + " winMargin " + winMargin + " alloc " + alloc);


                if (alloc > winMargin) {
                  int goals = takeFrom.getHalfNumPlayerGoals();
                  //System.out.println(goals);
                  goalsToReallocate += goals;
                  takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                }
                else {
                  int goals = Math.min(takeFrom.getHalfNumPlayerGoals(), takeFrom.getNumPlayerGoals() - (takeFrom.getNumOpponentGoals() + alloc +1));
                  goals = Math.max(0,goals);
                  //System.out.println(goals);
                  takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                  goalsToReallocate += goals;
                }
              }
              else {
                //System.out.println("Pos " + pos + " draw " + takeFrom.getNumPlayerGoals() );
                int goals = takeFrom.getHalfNumPlayerGoals();
                //System.out.println(goals);
                takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                goalsToReallocate += goals;
              }

              //System.out.println(goalsToReallocate);

              pos++;

              if(goalsToReallocate >= needed){
                break;
              }
            }

            if (goalsToReallocate >= needed){
              draw.setNumPlayerGoals(draw.getNumPlayerGoals() + needed);
              goalsToReallocate -= needed;
            }
            else{
              draw.setNumPlayerGoals(draw.getNumPlayerGoals() + goalsToReallocate);
              goalsToReallocate = 0;
            }
          }
          else {
            draw.setNumPlayerGoals(draw.getNumPlayerGoals() + needed);
            goalsToReallocate -= needed;
          }
        }

        //lossunder6
        for(Game loss: lostGames){
          //System.out.println(getMargin(loss));
          //System.out.println(loss.getScoreAsString());
          if(getMargin(loss) < 4 || getMargin(loss) > 6){
            break;
          }
          else {
            //System.out.println(loss.getScoreAsString());
            int needed = Math.min(lossUnder6, (Game.getMaxGoalThreshold() - loss.getNumPlayerGoals()));
            //System.out.println("LossesUnder6 " + lossUnder6 + " needed " + needed);
            //System.out.println(goalsToReallocate);
            if (goalsToReallocate < needed){
              while(pos<wonGames.size()){

                Game takeFrom = wonGames.get(pos);

                if(takeFrom.getNumPlayerGoals() > takeFrom.getNumOpponentGoals()){
                  int winMargin = getMargin(takeFrom);
                  int alloc = getAllocationCapacity(gameHistory, round, takeFrom.getID());

                  //System.out.println("Pos " + pos + " winMargin " + winMargin + " alloc " + alloc);


                  if (alloc > winMargin) {
                    int goals = takeFrom.getHalfNumPlayerGoals();
                    //System.out.println(goals);
                    goalsToReallocate += goals;
                    takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                  }
                  else {
                    int goals = Math.min(takeFrom.getHalfNumPlayerGoals(), takeFrom.getNumPlayerGoals() - (takeFrom.getNumOpponentGoals() + alloc +1));
                    goals = Math.max(0,goals);
                    //System.out.println(goals);
                    takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                    goalsToReallocate += goals;
                  }
                }
                else {
                  //System.out.println("Pos " + pos + " draw " + takeFrom.getNumPlayerGoals() );
                  int goals = takeFrom.getHalfNumPlayerGoals();
                  //System.out.println(goals);
                  takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                  goalsToReallocate += goals;
                }

                //System.out.println(goalsToReallocate);

                pos++;

                if(goalsToReallocate >= needed){
                  break;
                }
              }

              if (goalsToReallocate >= needed){
                loss.setNumPlayerGoals(loss.getNumPlayerGoals() + needed);
                goalsToReallocate -= needed;
              }
              else{
                loss.setNumPlayerGoals(loss.getNumPlayerGoals() + goalsToReallocate);
                goalsToReallocate = 0;
              }
            }
            else {
              loss.setNumPlayerGoals(loss.getNumPlayerGoals() + needed);
              goalsToReallocate -= needed;
            }
          }

          //System.out.println(loss.getScoreAsString());
        }

        if (drawOf3.size() > 1)
          Collections.sort(drawOf3, rangeComparatorDraw);

        // draw of 3
        for(Game draw: drawOf3){
         //System.out.println(draw.getScoreAsString());
          int needed = Math.min(lossUnder6, (Game.getMaxGoalThreshold() - draw.getNumPlayerGoals()));
          //System.out.println("LossesUnder6 " + lossUnder6 + " needed " + needed);
          //System.out.println(goalsToReallocate);
          if (goalsToReallocate < needed){
            while(pos<wonGames.size()){

              Game takeFrom = wonGames.get(pos);

              if(takeFrom.getNumPlayerGoals() > takeFrom.getNumOpponentGoals()){
                int winMargin = getMargin(takeFrom);
                int alloc = getAllocationCapacity(gameHistory, round, takeFrom.getID());

                //System.out.println("Pos " + pos + " winMargin " + winMargin + " alloc " + alloc);


                if (alloc > winMargin) {
                  int goals = takeFrom.getHalfNumPlayerGoals();
                  //System.out.println(goals);
                  goalsToReallocate += goals;
                  takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                }
                else {
                  int goals = Math.min(takeFrom.getHalfNumPlayerGoals(), takeFrom.getNumPlayerGoals() - (takeFrom.getNumOpponentGoals() + alloc +1));
                  goals = Math.max(0,goals);
                  //System.out.println(goals);
                  takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                  goalsToReallocate += goals;
                }
              }
              else {
                //System.out.println("Pos " + pos + " draw " + takeFrom.getNumPlayerGoals() );
                int goals = takeFrom.getHalfNumPlayerGoals();
                //System.out.println(goals);
                takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                goalsToReallocate += goals;
              }

              //System.out.println(goalsToReallocate);

              pos++;

              if(goalsToReallocate >= needed){
                break;
              }
            }

            if (goalsToReallocate >= needed){
              draw.setNumPlayerGoals(draw.getNumPlayerGoals() + needed);
              goalsToReallocate -= needed;
            }
            else{
              draw.setNumPlayerGoals(draw.getNumPlayerGoals() + goalsToReallocate);
              goalsToReallocate = 0;
            }
          }
          else {
            draw.setNumPlayerGoals(draw.getNumPlayerGoals() + needed);
            goalsToReallocate -= needed;
          }
        }

        //lossunder6
        for(Game loss: lostGames){
          //System.out.println(getMargin(loss));
          //System.out.println(loss.getScoreAsString());
          if(getMargin(loss) < 7){
            break;
          }
          else {
            //System.out.println(loss.getScoreAsString());
            int needed = Math.min(lossUnder8, (Game.getMaxGoalThreshold() - loss.getNumPlayerGoals()));
            //System.out.println("LossesUnder8 " + lossUnder8 + " needed " + needed);
            //System.out.println(goalsToReallocate);
            if (goalsToReallocate < needed){
              while(pos<wonGames.size()){

                Game takeFrom = wonGames.get(pos);

                if(takeFrom.getNumPlayerGoals() > takeFrom.getNumOpponentGoals()){
                  int winMargin = getMargin(takeFrom);
                  int alloc = getAllocationCapacity(gameHistory, round, takeFrom.getID());

                  //System.out.println("Pos " + pos + " winMargin " + winMargin + " alloc " + alloc);


                  if (alloc > winMargin) {
                    int goals = takeFrom.getHalfNumPlayerGoals();
                    //System.out.println(goals);
                    goalsToReallocate += goals;
                    takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                  }
                  else {
                    int goals = Math.min(takeFrom.getHalfNumPlayerGoals(), takeFrom.getNumPlayerGoals() - (takeFrom.getNumOpponentGoals() + alloc +1));
                    goals = Math.max(0,goals);
                    //System.out.println(goals);
                    takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                    goalsToReallocate += goals;
                  }
                }
                else {
                  //System.out.println("Pos " + pos + " draw " + takeFrom.getNumPlayerGoals() );
                  int goals = takeFrom.getHalfNumPlayerGoals();
                  //System.out.println(goals);
                  takeFrom.setNumPlayerGoals(takeFrom.getNumPlayerGoals() - goals);
                  goalsToReallocate += goals;
                }

                //System.out.println(goalsToReallocate);

                pos++;

                if(goalsToReallocate >= needed){
                  break;
                }
              }

              if (goalsToReallocate >= needed){
                loss.setNumPlayerGoals(loss.getNumPlayerGoals() + needed);
                goalsToReallocate -= needed;
              }
              else{
                loss.setNumPlayerGoals(loss.getNumPlayerGoals() + goalsToReallocate);
                goalsToReallocate = 0;
              }
            }
            else {
              loss.setNumPlayerGoals(loss.getNumPlayerGoals() + needed);
              goalsToReallocate -= needed;
            }
          }

          //System.out.println(loss.getScoreAsString());
        }


        if (goalsToReallocate > 0){
          wonGames.get(pos--).setNumPlayerGoals(wonGames.get(pos--).getNumPlayerGoals() + goalsToReallocate);
          goalsToReallocate = 0;
        }

        reallocatedPlayerGames.addAll(lostGames);
        reallocatedPlayerGames.addAll(drawOf2);
        reallocatedPlayerGames.addAll(drawOf3);
        reallocatedPlayerGames.addAll(wonGames);

        List<Game> finalGames = ConstraintExploit.targetedReallocation(targetTeamList, reallocatedPlayerGames);

        for (Game game : playerGames)
            simPrinter.println("OrigGame " + game.getID() + ": " +game.getScoreAsString());

        for (Game game : reallocatedPlayerGames)
            simPrinter.println("TreeReallocGame " + game.getID() + ": " +game.getScoreAsString());

        for (Game game : finalGames)
            simPrinter.println("ExploitGame " + game.getID() + ": " +game.getScoreAsString());

 		simPrinter.println("EXPLOIT (TREE) CONSTRAINTS TEST: " + checkConstraintsSatisfiedTest(playerGames, finalGames));
        simPrinter.println("TREE CONSTRAINTS TEST: " + checkConstraintsSatisfiedTest(playerGames, reallocatedPlayerGames));

        if(!targetTeamList.isEmpty())
        	if(checkConstraintsSatisfied(playerGames, finalGames)){
               simPrinter.println("TARGETED (UNDER TREE) RETURN");
               return finalGames;
           }
        else if(checkConstraintsSatisfied(playerGames, reallocatedPlayerGames)){
        	simPrinter.println("TREE RETURN");
        	return reallocatedPlayerGames;
        	
        }
        simPrinter.println("Returning RANDOM");
        finalGames = randomReallocate(playerGames);
        return finalGames;
	}

	private List<Game> regressionReallocation(Integer round, GameHistory gameHistory, List<Game> playerGames, Map<Integer, List<Game>> opponentGamesMap) {
		List<Game> reallocatedPlayerGames = new ArrayList<>();
		Map<Integer, MultipleLinearRegression> regressionMap = new HashMap<Integer, MultipleLinearRegression>();

		List<Game> wonGames = getWinningGames(playerGames);
		List<Game> drawnGames = getDrawnGames(playerGames);
		List<Game> lostGames = getLosingGames(playerGames);

		List<Integer> targetTeamList = new ArrayList<>();
		if(round > 2)
			targetTeamList = ConstraintExploit.targetScan(round, this.teamID, gameHistory);
		simPrinter.println(targetTeamList);

		int goalBank = 0;

		for(Game game : wonGames) {
			goalBank += game.getHalfNumPlayerGoals();
		}
		for(Game game : drawnGames) {
			goalBank += game.getHalfNumPlayerGoals();
		}

		for(Game game : playerGames) {
			regressionMap.put(game.getID(), getTeamRegression(round, gameHistory, game.getID()));
		}

		Comparator<Game> R2Comparator = (Game g1, Game g2) ->
		{return (int) Math.round((regressionMap.get(g1.getID()).R2() - regressionMap.get(g2.getID()).R2())*1000);};

		Collections.sort(lostGames, R2Comparator.reversed());
		Collections.sort(drawnGames, R2Comparator.reversed());

		int usedGoals = 0;
		int margin = 1;
		int maxMargin = 2;
		double accuracyThresh = 0.75;
		double accuracyMax = 0.95;
		int discrete = 0;
		for(Game game : lostGames) {
			int range = Math.abs(game.getNumPlayerGoals()-game.getNumOpponentGoals());
			if(game.getNumPlayerGoals() < game.getNumOpponentGoals()) discrete=0;
			if(game.getNumPlayerGoals() > game.getNumOpponentGoals()) discrete=3;
			if(game.getNumPlayerGoals() == game.getNumOpponentGoals()) discrete=1;
			int prediction = (int)Math.round(regressionMap.get(game.getID()).beta(0) + regressionMap.get(game.getID()).beta(1) * game.getNumOpponentGoals()
				+ regressionMap.get(game.getID()).beta(2) * game.getNumPlayerGoals() + regressionMap.get(game.getID()).beta(3) * discrete);
			int newScore = Math.max(prediction + margin, game.getNumPlayerGoals());
		//System.out.println("ANTES DERROTA: " + game.getScoreAsString());
				if(newScore == prediction + margin) {
					if(regressionMap.get(game.getID()).R2() >= accuracyMax &&
						goalBank > (newScore - game.getNumPlayerGoals()) && (newScore) <= 8) {
						goalBank -= (newScore - game.getNumPlayerGoals());
					usedGoals += (newScore - game.getNumPlayerGoals());
					game.setNumPlayerGoals(newScore);
				}
				else if(goalBank > (newScore - game.getNumPlayerGoals()) && (newScore) <= 8
					&& (range < maxMargin) && regressionMap.get(game.getID()).R2() > accuracyThresh) {
					goalBank -= (newScore - game.getNumPlayerGoals());
				usedGoals += (newScore - game.getNumPlayerGoals());
				game.setNumPlayerGoals(newScore);
			}
		}
		//System.out.println("DEPOIS DERROTA: "+ game.getScoreAsString());
		}
		for(Game game : drawnGames) {
			int range = Math.abs(game.getNumPlayerGoals()-game.getNumOpponentGoals());
			if(game.getNumPlayerGoals() < game.getNumOpponentGoals()) discrete=0;
			else if(game.getNumPlayerGoals() > game.getNumOpponentGoals()) discrete=3;
			else if(game.getNumPlayerGoals() == game.getNumOpponentGoals()) discrete=1;
			int prediction = (int)Math.round(regressionMap.get(game.getID()).beta(0) + regressionMap.get(game.getID()).beta(1) * game.getNumOpponentGoals()
				+ regressionMap.get(game.getID()).beta(2) * game.getNumPlayerGoals() + regressionMap.get(game.getID()).beta(3) * discrete);
		//System.out.println("ANTES EMPATE: " + game.getScoreAsString());
			if(usedGoals > 0) {
				int takeOut = Math.min(usedGoals, game.getHalfNumPlayerGoals());
				usedGoals -= takeOut;
				game.setNumPlayerGoals(game.getNumPlayerGoals()-takeOut);
			}
			int newScore = Math.max(prediction + margin, game.getNumPlayerGoals());
			if(newScore == prediction + margin) {
				if(regressionMap.get(game.getID()).R2() >= accuracyMax &&
					goalBank > (newScore - game.getNumPlayerGoals()) && (newScore) <= 8) {
					goalBank -= (newScore - game.getNumPlayerGoals());
				usedGoals += (newScore - game.getNumPlayerGoals());
				game.setNumPlayerGoals(newScore);
			}
			else if(goalBank > (newScore - game.getNumPlayerGoals()) && (newScore) <= 8
				&& (range < maxMargin) && regressionMap.get(game.getID()).R2() > accuracyThresh) {
				goalBank -= (newScore - game.getNumPlayerGoals());
			usedGoals += (newScore - game.getNumPlayerGoals());
			game.setNumPlayerGoals(newScore);
		}
		}
		//System.out.println("DEPOIS EMPATE: "+ game.getScoreAsString());
		}

		Comparator<Game> goalsComparatorWon = (Game g1, Game g2) ->
		{return (g1.getNumPlayerGoals()) - (g2.getNumPlayerGoals());};

		Collections.sort(wonGames, goalsComparatorWon.reversed());

		for(Game game : wonGames) {
			int range = Math.abs(game.getNumPlayerGoals()-game.getNumOpponentGoals());
			if(game.getNumPlayerGoals() < game.getNumOpponentGoals()) discrete=0;
			else if(game.getNumPlayerGoals() > game.getNumOpponentGoals()) discrete=3;
			else if(game.getNumPlayerGoals() == game.getNumOpponentGoals()) discrete=1;
			int prediction = (int)Math.round(regressionMap.get(game.getID()).beta(0) + regressionMap.get(game.getID()).beta(1) * game.getNumOpponentGoals()
				+ regressionMap.get(game.getID()).beta(2) * game.getNumPlayerGoals() + regressionMap.get(game.getID()).beta(3) * discrete);
		//System.out.println("ANTES VITORIA: " + game.getScoreAsString());
			if(usedGoals > 0) {
				int takeOut = Math.min(usedGoals, game.getHalfNumPlayerGoals());
				usedGoals -= takeOut;
				game.setNumPlayerGoals(game.getNumPlayerGoals()-takeOut);
			}
		//System.out.println("DEPOIS VITORIA: " + game.getScoreAsString());
		}

		reallocatedPlayerGames.addAll(wonGames);
		reallocatedPlayerGames.addAll(drawnGames);
		reallocatedPlayerGames.addAll(lostGames);

		List<Game> finalGames = ConstraintExploit.targetedReallocation(targetTeamList, reallocatedPlayerGames);

        for (Game game : playerGames)
            simPrinter.println("OrigGame " + game.getID() + ": " +game.getScoreAsString());

        for (Game game : reallocatedPlayerGames)
            simPrinter.println("Regressiongame: " + game.getID() + ": " +game.getScoreAsString());

        for (Game game : finalGames)
            simPrinter.println("ExploitGame " + game.getID() + ": " +game.getScoreAsString());

 		simPrinter.println("EXPLOIT (REGRESSION) CONSTRAINTS TEST: " + checkConstraintsSatisfiedTest(playerGames, finalGames));
        simPrinter.println("REGRESSION CONSTRAINTS TEST: " + checkConstraintsSatisfiedTest(playerGames, reallocatedPlayerGames));

        if(!targetTeamList.isEmpty())
        	if(checkConstraintsSatisfied(playerGames, finalGames)){
               simPrinter.println("TARGETED (UNDER REGRESSION) RETURN");
               return finalGames;
           }
        else if(checkConstraintsSatisfied(playerGames, reallocatedPlayerGames)){
        	simPrinter.println("REGRESSION RETURN");
        	return reallocatedPlayerGames;
        	
        }
        simPrinter.println("Returning RANDOM");
        finalGames = randomReallocate(playerGames);
        return finalGames;
	}

	public static String checkConstraintsSatisfiedTest(List<Game> originalPlayerGames, List<Game> reallocatedPlayerGames) {
			Map<Integer, Game> originalPlayerGamesMap = new HashMap<>();
			for(Game originalPlayerGame : originalPlayerGames)
				originalPlayerGamesMap.put(originalPlayerGame.getID(), originalPlayerGame);
			Map<Integer, Game> reallocatedPlayerGamesMap = new HashMap<>();
			for(Game reallocatedPlayerGame : reallocatedPlayerGames)
				reallocatedPlayerGamesMap.put(reallocatedPlayerGame.getID(), reallocatedPlayerGame);

			int totalNumOriginalPlayerGoals = 0, totalNumReallocatedPlayerGoals = 0;
			for(Game originalPlayerGame : originalPlayerGames) {
				if(!reallocatedPlayerGamesMap.containsKey(originalPlayerGame.getID()))
					continue;
				Game reallocatedPlayerGame = reallocatedPlayerGamesMap.get(originalPlayerGame.getID());
				boolean isOriginalWinningGame = hasWonGame(originalPlayerGame);
				boolean isOriginalLosingGame = hasLostGame(originalPlayerGame);
				boolean isOriginalDrawnGame = hasDrawnGame(originalPlayerGame);

				// Constraint 1
				if(reallocatedPlayerGame.getNumPlayerGoals() < 0 || reallocatedPlayerGame.getNumPlayerGoals() > Game.getMaxGoalThreshold())
					return "CONSTRAINT 1";

				// Constraint 2
				if(!originalPlayerGame.getNumOpponentGoals().equals(reallocatedPlayerGame.getNumOpponentGoals()))
					return "CONSTRAINT 2";

				// Constraint 3
				boolean numPlayerGoalsIncreased = reallocatedPlayerGame.getNumPlayerGoals() > originalPlayerGame.getNumPlayerGoals();
				if(isOriginalWinningGame && numPlayerGoalsIncreased)
					return "CONSTRAINT 3";

				// Constraint 4
				int halfNumPlayerGoals = originalPlayerGame.getHalfNumPlayerGoals();
				boolean numReallocatedPlayerGoalsLessThanHalf =
				reallocatedPlayerGame.getNumPlayerGoals() < (originalPlayerGame.getNumPlayerGoals() - halfNumPlayerGoals);
				if((isOriginalWinningGame || isOriginalDrawnGame) && numReallocatedPlayerGoalsLessThanHalf)
					return "CONSTRAINT 4";

				totalNumOriginalPlayerGoals += originalPlayerGame.getNumPlayerGoals();
				totalNumReallocatedPlayerGoals += reallocatedPlayerGame.getNumPlayerGoals();

				// Constraint 5
				boolean numPlayerGoalsDecreased = reallocatedPlayerGame.getNumPlayerGoals() < originalPlayerGame.getNumPlayerGoals();
				if(isOriginalLosingGame && numPlayerGoalsDecreased)
					return "CONSTRAINT 5";

			}

				// Constraint 6
			if(totalNumOriginalPlayerGoals != totalNumReallocatedPlayerGoals) {
				System.out.println(totalNumOriginalPlayerGoals + " : " + totalNumReallocatedPlayerGoals);
				return "CONSTRAINT 6";
			}
			return "CONSTRAINTS PASSED";
	}

	public MultipleLinearRegression getTeamRegression(Integer round, GameHistory gameHistory, Integer id) {
		double[][] x = new double[(round-1)*9][4];
		for(int i = 0; i < round-1; i++) {
			for(int j = 0; j < 9; j++) {
				x[i*9+j][0]=1;
				x[i*9+j][1]=gameHistory.getAllGamesMap().get(i).get(id).get(j).getScore().getNumPlayerGoals();
				x[i*9+j][2]=gameHistory.getAllGamesMap().get(i).get(id).get(j).getScore().getNumOpponentGoals();
				if(x[i*9+j][1] < x[i*9+j][2]) x[i*9+j][3]=0;
				else if(x[i*9+j][1] > x[i*9+j][2]) x[i*9+j][3]=3;
				else if(x[i*9+j][1] == x[i*9+j][2]) x[i*9+j][3]=1;
			}
		}
		double[] y = new double[(round-1)*9];
		for(int i = 1; i < round; i++) {
			for (int j = 0; j < 9; j++) {
				y[(i - 1) * 9 + j] = gameHistory.getAllGamesMap().get(i).get(id).get(j).getScore().getNumPlayerGoals();
			}
		}
		return new MultipleLinearRegression(x, y);
	}
	private int getAllocationCapacity(GameHistory gameHistory, Integer round, int gameID){
        List<Game> opponentsGames = gameHistory.getAllGamesMap().get(round-1).get(gameID);
        List<Game> winningGames = getWinningGames(opponentsGames);

        int allocationMargin = 0;

        for (Game win : winningGames){
              allocationMargin += win.getHalfNumPlayerGoals();
        }

        if (allocationMargin == 0){
              return 0;
        }

        if (winningGames.size() == 9){
              return 100;
        }

        return (int) Math.ceil(allocationMargin/(9-winningGames.size()));
    }

    private int getMargin(Game game){
        return Math.abs(game.getNumPlayerGoals() - game.getNumOpponentGoals());
    }

	private List<Game> randomReallocate(List<Game> playerGames){
		List<Game> reallocatedPlayerGames = new ArrayList<>();
    	List<Game> wonGames = getWinningGames(playerGames);
    	List<Game> drawnGames = getDrawnGames(playerGames);
    	List<Game> lostGames = getLosingGames(playerGames);
    	 
    	List<Game> lostOrDrawnGamesWithReallocationCapacity = new ArrayList<>(lostGames);
    	lostOrDrawnGamesWithReallocationCapacity.addAll(drawnGames);
    	for(Game lostGame : lostGames)
    		if(lostGame.maxPlayerGoalsReached())
    			lostOrDrawnGamesWithReallocationCapacity.remove(lostGame);
    	 for(Game drawnGame : drawnGames)
    		 if(drawnGame.maxPlayerGoalsReached())
    			 lostOrDrawnGamesWithReallocationCapacity.remove(drawnGame);
   	 
    	 for(Game winningGame : wonGames) {    		 
    		 
    		 if(lostOrDrawnGamesWithReallocationCapacity.size() == 0)
    			 break;

    		 Game randomLostOrDrawnGame = lostOrDrawnGamesWithReallocationCapacity.get(this.random.nextInt(lostOrDrawnGamesWithReallocationCapacity.size()));

    		 int halfNumPlayerGoals = winningGame.getHalfNumPlayerGoals();
    		 int numRandomGoals = (int) Math.min(this.random.nextInt(halfNumPlayerGoals) + 1, Game.getMaxGoalThreshold() - randomLostOrDrawnGame.getNumPlayerGoals());

    		 winningGame.setNumPlayerGoals(winningGame.getNumPlayerGoals() - numRandomGoals);
    		 randomLostOrDrawnGame.setNumPlayerGoals(randomLostOrDrawnGame.getNumPlayerGoals() + numRandomGoals);
    		 
    		 if(randomLostOrDrawnGame.maxPlayerGoalsReached())
    			 lostOrDrawnGamesWithReallocationCapacity.remove(randomLostOrDrawnGame);
    	 }
    	 
    	 reallocatedPlayerGames.addAll(wonGames);
    	 reallocatedPlayerGames.addAll(drawnGames);
    	 reallocatedPlayerGames.addAll(lostGames);

    	 if(checkConstraintsSatisfied(playerGames, reallocatedPlayerGames))
    		 return reallocatedPlayerGames;
    	 return playerGames;
    }
	private List<Game> getWinningGames(List<Game> playerGames) {
		List<Game> winningGames = new ArrayList<>();
		for(Game game : playerGames) {
			int numPlayerGoals = game.getNumPlayerGoals();
			int numOpponentGoals = game.getNumOpponentGoals();
			if(numPlayerGoals > numOpponentGoals)
				winningGames.add(game.cloneGame());
		}
		return winningGames;
	}
	private List<Game> getDrawnGames(List<Game> playerGames) {
		List<Game> drawnGames = new ArrayList<>();
		for(Game game : playerGames) {
			int numPlayerGoals = game.getNumPlayerGoals();
			int numOpponentGoals = game.getNumOpponentGoals();
			if(numPlayerGoals == numOpponentGoals)
				drawnGames.add(game.cloneGame());
		}
		return drawnGames;
	}
	private List<Game> getLosingGames(List<Game> playerGames) {
		List<Game> losingGames = new ArrayList<>();
		for(Game game : playerGames) {
			int numPlayerGoals = game.getNumPlayerGoals();
			int numOpponentGoals = game.getNumOpponentGoals();
			if(numPlayerGoals < numOpponentGoals)
				losingGames.add(game.cloneGame());
		}
		return losingGames;
	}
}