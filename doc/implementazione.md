## Implementazione 

In questo capitolo è presente una discussione delle scelte implementative effettuate dai membri del team di sviluppo.

### Programmazione funzionale

Dato il contesto di sviluppo di questo progetto si è cercato di utilizzare il più possibile il paradigma funzionale. Di seguito sono elencati alcuni aspetti ritenuti rilevanti per comprendendere come questo tipo di paradigma sia stato sfruttato.

#### For comprehension 
Questo costrutto è basato sulle monadi e risulta molto utile per aumentare la leggibilità del codice e limitare l'approccio imperativo. In questo progetto è stato utilizzato in accoppiata con i Task della libreria [Monix](https://monix.io/). Di seguito un metodo, estratto dalla classe `SimulationEngine`, che rappresenta un singolo step della simulazione:
```scala
override def simulationStep(): Task[Unit] =
  for
    _ <- moveCars()
    _ <- updateStanding()
    _ <- updateView()
    _ <- waitFor(speedManager.simulationSpeed)
    _ <- checkEnd()
  yield ()
```

#### Pattern matching
Questo meccanismo permette di eseguire un match fra un valore e un dato pattern, ha una sintassi particolarmente idiomatica ed è stato usato principlamente in due accezioni:
1. Nel modo classico, come fosse una sorta di switch di Java più potente. Ad esempio:
```scala
val alpha = direction match
    case Direction.Forward => 0
    case Direction.Backward => 180
```
2. Per decomporre un oggetto nelle parti che lo costituiscono. Ad esempio, è stato molto utile nella classe `CarsLoader`: dopo aver effettuato la query prolog per caricare tutte le macchine si hanno i parametri di una data macchina in una lista, ogni elemento di questa lista può essere selezionato in modo maggiormente comprensibile grazie a questo meccanismo come segue:
```scala
 private def mkCar(params: List[String], track: Track): Car = params match {
    case List(path, name, tyre, skills, maxSpeed, acceleration, actualSector, fuel, carColor) => 
      ...
```

#### Higher-order functions
Il meccanismo delle higher-order functions permette di implementare funzioni che prendono come input un'altra funzione (o in generale più di una) e/o ritornano una funzione. Questo permette di implementare in maniera molto semplice il pattern strategy ed aumenta il riutilizzo del codice. 
```scala 
private def updateParameter[E](sector: Sector, onStraight: () => E, onTurn: () => E): E = sector match
    case s: Straight => onStraight()
    case t: Turn => onTurn()
```

#### Option 
In programmazione funzionale la classe `Option` è utiizzata per rappresentare la possibile assenza di un valore, evitando di utilizzare il valore `null`. In questo progetto è stata utilizzata nella classe `ControllerModule`, l'esigenza era di rappresentare la presenza di un valore per la variabile `stopFuture` solo nel caso in cui la simulazione sia effettivamente stata fatta partire.
```scala 
private var stopFuture: Option[Cancelable] = None

override def notifyStart(): Unit = stopFuture = Some(
  ....
)
```

#### Either
Questo meccanismo è utilizzato per la gestione delle eccezioni, un valore di tipo `Either[+A, +B]` rappresenta un valore che potrebbe assumere sia tipo `A` che tipo `B`. Questo rende particolarmente comodo la gestione delle eccezioni in quanto si può usare un valore di tipo `Either[Throwable, A]`. Nel caso specifico è stato utilizzato nella classe `ControllerModule` dato che viene ritornato dal metodo `runAsync` dei Task di Monix. Una volta ottenuto un valore di questo tipo lo si può gestire con una partial function che esprime le computazioni da intraprendere nei due casi.

```scala
context.simulationEngine
    .simulationStep()
    .loopForever
    .runAsync {
      case Left(exp) => global.reportFailure(exp)
      case _ =>
    }
```

#### Mixins
Il meccanismo dei mixins permette di aggregare insieme più classi mediante composizione, invece che mediante ereditarietà. Un esempio di utilizzo è presente nella classe `ControllerModule`:
```scala
trait Interface extends Provider with Component:
```

### Programmazione asincrona

La programmazione asincrona è stata sfruttata per avere un'interfaccia responsive delegando le computazioni pesanti (e.g. la simulazione vera e propria) ad entità terze. Per raggiungere questo obiettivo sono stati sfruttati i Task di Monix. Un esempio di facile comprensione è il metodo `notifyStart` della classe `ControllerModule`:
```scala
override def notifyStart(): Unit = stopFuture = Some(
    context.simulationEngine
      .simulationStep()
      .loopForever
      .runAsync {
        case Left(exp) => global.reportFailure(exp)
        case _ =>
      }
)
```

### Programmazione reattiva
La programmazione reattiva è stata sfruttata per implementare l'aggiornamento automatico i grafici, difatti, ogni volta che uno `snapshot` viene aggiunto alla `history` della simulazione viene, in automatico, richiamato il metodo di aggiornamento dei vari grafici. Anche questa parte è stata realizzata sfruttando le API di Monix. Nella `ModelModule`, che contiene la storia della simulazione, è necessario aggiungere tre elementi:
1. Un wrapper della `history` che rappresenta l'entità da osservare;
```scala
private var history: List[Snapshot] = List.empty
private val historySubject = ConcurrentSubject[List[Snapshot]](MulticastStrategy.publish)
```
2. Un metodo per registrare le callback su tale subject;
```scala
override def registerCallbackHistory(onNext: List[Snapshot] => Future[Ack], onError: Throwable => Unit, onComplete: () => Unit): Cancelable =
  historySubject.subscribe(onNext, onError, onComplete)
```
3. La chiamata al metodo di notifica del subject per comunicare una sua variazione, nel nostro caso ogni volta che viene aggiunto uno snapshot.
```scala
override def addSnapshot(snapshot: Snapshot): Unit =
  history = history :+ snapshot
  historySubject.onNext(history)
```

L'ultimo passo necessario è la chiamata effettiva al metodo per registrare la callback, questa avviene nel `ControllerModule`:
```scala
 override def registerReactiveChartCallback(): Unit =
    val onNext = (l: List[Snapshot]) => 
      context.view.updateCharts(l)
      Ack.Continue
    val onError = (t: Throwable) => ()
    val onComplete = () => ()
    context.model.registerCallbackHistory(onNext, onError, onComplete)

```

### Programmazione logica
Il paradigma di programmazione logico è stato utilizzato, all'interno di questo progetto, per due scopi diversi:
1. Avere una sorta di database lightweight per fornire all'applicativo la pista e le vetture. Dunque se si volesse implementare una nuova pista basterebbe: implementare nella classe `TrackBuilder` il metodo di caricamento di tale pista e fornire un nuovo file prolog contenente le regole che descrivono i suoi settori, come ad esempio:
  ```prolog
  straight(id(1), startPointE(181, 113), endPointE(725, 113), startPointI(181, 170), endPointI(725, 170)).
  ```
  
2. Effettuare i calcoli per i movimenti delle macchine nei rettilinei.


### Sezioni personali

#### Davide Domini
Nello sviluppo del progetto, inizialmente, mi sono occupato insieme a Matteucci della realizzazione della schermata per visualizzare l'andamento della simulazione. Questo comprende lo sviluppo delle classi:
- `SimulationPanel`
- `Environment`
- `TrackBuilder`
- `CarsLoader`

Successivamente, sempre in collaborazione con Matteucci, sono passato allo sviluppo della parte del `SimulationEngine` legata al movimento delle macchine durante la simulazione. Questo comprende le classi:
- `SimulationEngineModule`
- `Movements`

Infine, in autonomia, ho sviluppato:
- Gestione dei grafici, classe `LineChart` e aggiornamento automatico tramite programmazione reattiva;
- Object `PimpScala`, in cui vengono arricchite alcune entità esistenti come: Int, Tuple2, Option, HashMap e JPanel;
- Meccanismo per calcolare il degrado delle gomme;
- Start e stop della simulazione tramite programmazione asincrona.

#### Alessandro Magnani
In un primo momento, ho sviluppato insieme a Montanari la schermata iniziale del simulatore, nella quale è possibile stabilire i parametri delle diverse macchine selezionabili, la griglia di partenza, il numero di giri della gara e i parametri relativi al singolo pilota. Le classi coinvolte sono le seguenti:
- `MainPanel`
- `CarSelectionPanel`
- `ParamsSelectionPanel`
- `StartSimulationPanel`
- `StartingPositionsPanel`

Successivamente, sempre cooperando con Montanari, ho implementato il numero di giri completati e la classifica in real-time con tutte le relative informazioni per ogni vettura all'interno del pannello della simulazione. Le implementazioni apportate coinvolgono le seguenti classi:
- `SimulationPanel`
- `Standings`
- `SimulationEngineModule`

Infine, sempre in collaborazione con Montanari, mi sono occupato del report finale della gara. Classi relative:
- `EndRacePanel`

#### Andrea Matteucci

#### Simone Montanari
Inizialmente, in collaborazione con Magnani, mi sono occupato della crezione della schermata iniziale del simulatore, dove è possibile impostare i parametri delle diverse macchine, la griglia di partenza ed il numero di giri della gara.
Classi relative:
- `MainPanel`
- `CarSelectionPanel`
- `ParamsSelectionPanel`
- `StartSimulationPanel`
- `StartingPositionsPanel`

Successivamente, in collaborazione con Domini, mi sono occupato dell'implementazione del movimento in curva delle macchine.
Classi relative:
- `SimulationEngineModule`
- `Movements`

Dopo aver implementato il movimento delle macchine, in collaborazione con Magnani, ho implementato la classifica in real-time della gara ed il numero di giri impostato inizialmente.
Classi relative:
- `SimulationPanel`
- `Standings`
- `SimulationEngineModule`

Infine, sempre in collaborazione con Magnani, mi sono occupato della realizzazione del report finale della gara.
Classi relative:
- `EndRacePanel`
      





