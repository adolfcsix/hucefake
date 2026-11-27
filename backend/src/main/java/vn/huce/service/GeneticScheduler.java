package vn.huce.service;

import vn.huce.Accounts;

import static vn.huce.validation.Inputs.*;
import static vn.huce.service.SchedulingService.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class GeneticScheduler {
    private final SchedulingService scheduling;
    private final GeneticInputBuilder inputBuilder;

    GeneticScheduler(SchedulingService scheduling, GeneticInputBuilder inputBuilder) {
        this.scheduling = scheduling;
        this.inputBuilder = inputBuilder;
    }
    public record Parameters(int population,int generations,double crossover,double mutation,int seed){}
    record Input(Context ctx,List<List<Slot>> genes){}
    record Score(List<Slot> slots,int hard,double soft){}
    record Progress(int generation,int hard,double soft){}
    record Outcome(Score score,int generation,List<String> notes){}
    public static Parameters parameters(Object raw){
        if(raw!=null&&!(raw instanceof Map<?,?>))throw invalid("Tham số không hợp lệ.");
        Map<?,?> p=raw==null?Map.of():(Map<?,?>)raw;
        return new Parameters((int)number(or(p,"population",100),8,200),(int)number(or(p,"generations",500),1,1000),rate(or(p,"crossover",.9)),rate(or(p,"mutation",.02)),(int)number(or(p,"seed",new java.security.SecureRandom().nextInt()),Integer.MIN_VALUE,Integer.MAX_VALUE));
    }
    static Object or(Map<?,?> p,String key,Object fallback){Object v=p.get(key);return v==null?fallback:v;}
    static double rate(Object v){if(!(v instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<0||n.doubleValue()>1)throw invalid("Xác suất phải nằm trong khoảng 0 đến 1.");return n.doubleValue();}
    List<Map<String, Object>> targets(Context context, Accounts.Actor actor, long semesterId) {
        return inputBuilder.targets(context, actor, semesterId);
    }

    static Slot make(Map<String, Object> section, long roomId, int dayOfWeek, int periodFrom) {
        return GeneticInputBuilder.make(section, roomId, dayOfWeek, periodFrom);
    }

    Input input(Accounts.Actor actor, long semesterId) {
        return inputBuilder.build(actor, semesterId);
    }

    private static final Comparator<Score> ORDER=Comparator.comparingInt(Score::hard).thenComparingDouble(Score::soft);
    private Slot sample(Input data,int i,Random rng){var domain=data.genes.get(i);return domain.get(rng.nextInt(domain.size()));}
    private Score initial(Input input,GeneticFitnessEvaluator evaluator,Random rng,boolean greedy,long deadline){
        var slots=new ArrayList<Slot>();
        for(int i=0;i<input.genes.size();i++){
            Slot best=sample(input,i,rng);
            if(greedy){
                var cost=evaluator.base(best);int hard=cost.hard();for(var other:slots)hard+=evaluator.clash(best,other);double soft=cost.soft();
                for(int t=0;t<32&&System.nanoTime()<deadline;t++){
                    var candidate=sample(input,i,rng);var base=evaluator.base(candidate);int h=base.hard();for(var other:slots)h+=evaluator.clash(candidate,other);
                    if(h<hard||(h==hard&&base.soft()<soft)){best=candidate;hard=h;soft=base.soft();}
                }
            }
            slots.add(best);
        }
        return evaluator.score(slots);
    }
    private Score tournament(List<Score> population,Random rng){Score best=population.get(rng.nextInt(population.size()));for(int i=0;i<2;i++){var x=population.get(rng.nextInt(population.size()));if(ORDER.compare(x,best)<0)best=x;}return best;}
    Outcome solve(Input input,Parameters settings,Consumer<Progress> publish){
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(60),softDeadline=deadline-java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        var rng=new Random(settings.seed);var evaluator=new GeneticFitnessEvaluator(input, scheduling);var population=new ArrayList<Score>();
        for(int i=0;i<settings.population&&System.nanoTime()<deadline;i++)population.add(initial(input,evaluator,rng,i<settings.population/5,deadline));
        if(population.isEmpty())throw state("Không còn thời gian tạo quần thể.");
        population.sort(ORDER);var best=population.getFirst();int generation=0,stagnant=0;
        publish.accept(new Progress(0,best.hard,best.soft));
        while(generation<settings.generations&&stagnant<100&&(best.hard>0||best.soft>0)&&System.nanoTime()<(best.hard>0?deadline:softDeadline)){
            var next=new ArrayList<Score>(population.subList(0,Math.min(2,population.size())));
            while(next.size()<settings.population&&System.nanoTime()<(best.hard>0?deadline:softDeadline)){
                var a=tournament(population,rng);var b=tournament(population,rng);var slots=new ArrayList<Slot>();var groups=new HashMap<Long,Boolean>();boolean cross=rng.nextDouble()<settings.crossover;
                for(int i=0;i<a.slots.size();i++){var slot=a.slots.get(i);if(rng.nextDouble()<settings.mutation)slots.add(sample(input,i,rng));else slots.add(cross&&groups.computeIfAbsent(slot.section(),k->rng.nextBoolean())?b.slots.get(i):slot);}
                var child=evaluator.score(slots);
                if(child.hard>0&&rng.nextDouble()<.3&&System.nanoTime()<deadline){
                    int i=rng.nextInt(slots.size());for(int t=0;t<10&&System.nanoTime()<deadline;t++){var trial=new ArrayList<>(child.slots);trial.set(i,sample(input,i,rng));var attempt=evaluator.score(trial);if(ORDER.compare(attempt,child)<0)child=attempt;}
                }
                next.add(child);
            }
            next.sort(ORDER);population=next;generation++;
            if(ORDER.compare(population.getFirst(),best)<0){best=population.getFirst();stagnant=0;}else stagnant++;
            if(generation%5==0)publish.accept(new Progress(generation,best.hard,best.soft));
        }
        if(System.nanoTime()>=deadline&&best.hard>0)throw state("Vượt giới hạn 60 giây.");
        publish.accept(new Progress(generation,best.hard,best.soft));
        var notes=new ArrayList<String>();
        for(int i=0;i<best.slots.size();i++){
            var slot=best.slots.get(i);var messages=new LinkedHashSet<String>();
            for(var fixed:input.ctx.patterns())for(var issue:scheduling.pair(input.ctx,slot,fixed))messages.add(s(issue,"message"));
            for(int j=0;j<best.slots.size();j++)if(i!=j)for(var issue:scheduling.pair(input.ctx,slot,best.slots.get(j)))messages.add(s(issue,"message"));
            if(evaluator.busy(slot,"BUSY")>0)messages.add("Giảng viên bận trong khung tiết.");
            var message=String.join(" ",messages);notes.add(message.isEmpty()?null:message.substring(0,Math.min(1000,message.length())));
        }
        return new Outcome(best,generation,notes);
    }
}
