package vn.huce.service;

import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;

import static vn.huce.validation.Inputs.*;
import static vn.huce.service.SchedulingService.*;
import java.util.*;
import java.time.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class GeneticScheduler {
    private final SchedulingService scheduling;
    private final SqlStore db;
    GeneticScheduler(SchedulingService scheduling,SqlStore db){this.scheduling=scheduling;this.db=db;}
    record Parameters(int population,int generations,double crossover,double mutation,int seed){}
    record Input(Context ctx,List<List<Slot>> genes){}
    record Score(List<Slot> slots,int hard,double soft){}
    record Progress(int generation,int hard,double soft){}
    record Outcome(Score score,int generation,List<String> notes){}
    static Parameters parameters(Object raw){
        if(raw!=null&&!(raw instanceof Map<?,?>))throw invalid("Tham số không hợp lệ.");
        Map<?,?> p=raw==null?Map.of():(Map<?,?>)raw;
        return new Parameters((int)number(or(p,"population",100),8,200),(int)number(or(p,"generations",500),1,1000),rate(or(p,"crossover",.9)),rate(or(p,"mutation",.02)),(int)number(or(p,"seed",new java.security.SecureRandom().nextInt()),Integer.MIN_VALUE,Integer.MAX_VALUE));
    }
    static Object or(Map<?,?> p,String key,Object fallback){Object v=p.get(key);return v==null?fallback:v;}
    static double rate(Object v){if(!(v instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<0||n.doubleValue()>1)throw invalid("Xác suất phải nằm trong khoảng 0 đến 1.");return n.doubleValue();}
    List<Map<String,Object>> targets(Context ctx,Accounts.Actor a,long semester){
        return ctx.sections().values().stream().filter(s->n(s,"semester_id")==semester&&"DRAFT".equals(s.get("status"))&&("MANAGER".equals(a.role())||Objects.equals(a.unitId(),nullableId(s.get("managing_unit_id"))))&&n(s,"scheduled")<n(s,"sessions_per_week")).toList();
    }
    static Slot make(Map<String,Object> section,long room,int day,int first){
        return new Slot(0,n(section,"id"),nullableId(section.get("staff_id")),room,day,first,(int)n(section,"periods_per_session"),SchedulingService.day(section.get("start_date")),SchedulingService.day(section.get("end_date")));
    }
    Input input(Accounts.Actor a,long semester){
        var ctx=scheduling.context();var sections=targets(ctx,a,semester);var allowed=scheduling.campuses(a);
        if(sections.isEmpty())throw state("Không có buổi học nháp cần xếp trong phạm vi đã chọn.");
        var from=sections.stream().map(s->day(s.get("start_date"))).min(LocalDate::compareTo).orElseThrow();
        var to=sections.stream().map(s->day(s.get("end_date"))).max(LocalDate::compareTo).orElseThrow();
        var published=db.rows("""
          SELECT x.section_id,x.room_id,x.start_at,x.end_at,
          ARRAY[x.staff_id] || ARRAY(SELECT a.staff_id FROM section_assignments a WHERE a.section_id=x.section_id AND a.role='ASSISTANT' AND a.valid_to IS NULL) AS staff_ids,
          ARRAY(SELECT g.group_id FROM section_target_groups g WHERE g.section_id=x.section_id) AS group_ids
          FROM class_sessions x WHERE x.start_at<?::date+1 AND x.end_at>=?::date AND x.status<>'CANCELLED'
          """,java.sql.Date.valueOf(to),java.sql.Date.valueOf(from));
        var byDay=new HashMap<LocalDate,List<Map<String,Object>>>();
        for(var row:published){
            var start=LocalDateTime.parse(s(row,"start_at"));var end=LocalDateTime.parse(s(row,"end_at"));
            for(var d=start.toLocalDate();!d.isAfter(end.toLocalDate());d=d.plusDays(1))byDay.computeIfAbsent(d,k->new ArrayList<>()).add(row);
        }
        var genes=new ArrayList<List<Slot>>();var issues=new ArrayList<Map<String,Object>>();
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
        for(var section:sections){
            if(section.get("staff_id")==null){issues.add(scheduling.issue("BR02","Chưa có giảng viên chính.",section));continue;}
            var domain=new ArrayList<Slot>();
            for(var room:ctx.rooms().values())if(allowed.contains(n(room,"campus_id"))&&scheduling.fits(room,section)){
                for(int d=1;d<=6;d++)for(int p=1;p<=16-n(section,"periods_per_session");p++){
                    if(System.nanoTime()>deadline)throw state("Vượt giới hạn 60 giây tạo miền xếp lịch.");
                    var slot=make(section,n(room,"id"),d,p);boolean valid=true,teaching=false;
                    var staff=scheduling.staff(ctx,slot);var groups=ids(section.get("group_ids"));
                    for(var date:occurrences(slot)){
                        var time=scheduling.interval(ctx,slot,date);if(time==null){valid=false;break;}
                        if(scheduling.holiday(ctx,n(room,"campus_id"),date))continue;
                        teaching=true;
                        for(var row:byDay.getOrDefault(date,List.of())){
                            if(time.start().isBefore(LocalDateTime.parse(s(row,"end_at")))&&LocalDateTime.parse(s(row,"start_at")).isBefore(time.end())&&
                              (Objects.equals(nullableId(row.get("room_id")),slot.room())||n(row,"section_id")==slot.section()||!Collections.disjoint(staff,ids(row.get("staff_ids")))||!Collections.disjoint(groups,ids(row.get("group_ids"))))){valid=false;break;}
                        }
                        if(!valid)break;
                    }
                    if(valid&&teaching)domain.add(slot);
                }
            }
            if(domain.isEmpty()){issues.add(scheduling.issue("BR07","Không có phòng và khung tiết phù hợp.",section));continue;}
            var options=List.copyOf(domain);
            for(long i=n(section,"scheduled");i<n(section,"sessions_per_week");i++)genes.add(options);
        }
        if(!issues.isEmpty())throw new ApiFailure(400,"VALIDATION_ERROR","Dữ liệu chưa đủ để xếp lịch tự động.",Map.of("issues",issues));
        return new Input(ctx,List.copyOf(genes));
    }
    private record Base(int hard,double soft,Set<Long> staff,String shift){}
    private record Pair(Slot a,Slot b){}
    private class Evaluator {
        final Input input;final Map<Slot,Base> intrinsic=new HashMap<>();final Map<Pair,Integer> pairs=new HashMap<>();
        Evaluator(Input input){this.input=input;}
        int busy(Slot slot,String kind){
            var ctx=input.ctx;var section=ctx.sections().get(slot.section());var staff=scheduling.staff(ctx,slot);int count=0;
            for(int p=slot.first();p<=slot.last();p++){final int period=p;if(ctx.availability().stream().anyMatch(r->staff.contains(n(r,"staff_id"))&&n(r,"semester_id")==n(section,"semester_id")&&n(r,"day_of_week")==slot.day()&&kind.equals(r.get("kind"))&&n(r,"period_from")<=period&&n(r,"period_to")>=period))count++;}
            return count;
        }
        int clash(Slot a,Slot b){
            if(a.day()!=b.day())return 0;
            var key=new Pair(a,b);var found=pairs.get(key);if(found!=null)return found;
            int value=0;for(var issue:scheduling.pair(input.ctx,a,b))value+="BR09".equals(issue.get("rule"))?1:Math.max(1,Math.min(a.last(),b.last())-Math.max(a.first(),b.first())+1);
            if(pairs.size()<250000)pairs.put(key,value);return value;
        }
        Base base(Slot s){
            return intrinsic.computeIfAbsent(s,slot->{
                var ctx=input.ctx;var room=ctx.rooms().get(slot.room());var section=ctx.sections().get(slot.section());
                int hard=busy(slot,"BUSY");for(var fixed:ctx.patterns())hard+=clash(slot,fixed);
                double soft=5.0*busy(slot,"AVOID")+10.0*(n(room,"capacity")-n(section,"capacity"))/n(room,"capacity");
                var date=firstDay(slot.from(),slot.day());
                String shift=ctx.periods().stream().filter(p->n(p,"campus_id")==n(room,"campus_id")&&n(p,"period_no")==slot.first()&&!day(p.get("effective_from")).isAfter(date)&&(p.get("effective_to")==null||!day(p.get("effective_to")).isBefore(date))).findFirst().map(p->s(p,"shift")).orElse("");
                return new Base(hard,soft,scheduling.staff(ctx,slot),shift);
            });
        }
        Score score(List<Slot> slots){
            int hard=0;double soft=0;var daily=new HashMap<String,Integer>();var groupPeriods=new HashMap<String,TreeSet<Integer>>();
            for(int i=0;i<slots.size();i++){
                var slot=slots.get(i);var base=base(slot);hard+=base.hard;soft+=base.soft;
                for(int j=0;j<i;j++){
                    var other=slots.get(j);hard+=clash(slot,other);
                    if(slot.section()==other.section()&&Math.abs(slot.day()-other.day())==1)soft+=3;
                }
                for(long staff:base.staff)daily.merge(staff+":"+slot.day(),slot.count(),Integer::sum);
                for(long group:ids(input.ctx.sections().get(slot.section()).get("group_ids"))){
                    var set=groupPeriods.computeIfAbsent(group+":"+slot.day()+":"+base.shift+":"+slot.from()+":"+slot.to(),k->new TreeSet<>());
                    for(int p=slot.first();p<=slot.last();p++)set.add(p);
                }
            }
            for(int count:daily.values())soft+=4*Math.max(0,count-8);
            for(var p:groupPeriods.values())soft+=2*(p.last()-p.first()+1-p.size());
            return new Score(List.copyOf(slots),hard,soft);
        }
    }
    private static final Comparator<Score> ORDER=Comparator.comparingInt(Score::hard).thenComparingDouble(Score::soft);
    private Slot sample(Input data,int i,Random rng){var domain=data.genes.get(i);return domain.get(rng.nextInt(domain.size()));}
    private Score initial(Input input,Evaluator evaluator,Random rng,boolean greedy,long deadline){
        var slots=new ArrayList<Slot>();
        for(int i=0;i<input.genes.size();i++){
            Slot best=sample(input,i,rng);
            if(greedy){
                var cost=evaluator.base(best);int hard=cost.hard;for(var other:slots)hard+=evaluator.clash(best,other);double soft=cost.soft;
                for(int t=0;t<32&&System.nanoTime()<deadline;t++){
                    var candidate=sample(input,i,rng);var base=evaluator.base(candidate);int h=base.hard;for(var other:slots)h+=evaluator.clash(candidate,other);
                    if(h<hard||(h==hard&&base.soft<soft)){best=candidate;hard=h;soft=base.soft;}
                }
            }
            slots.add(best);
        }
        return evaluator.score(slots);
    }
    private Score tournament(List<Score> population,Random rng){Score best=population.get(rng.nextInt(population.size()));for(int i=0;i<2;i++){var x=population.get(rng.nextInt(population.size()));if(ORDER.compare(x,best)<0)best=x;}return best;}
    Outcome solve(Input input,Parameters settings,Consumer<Progress> publish){
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(60),softDeadline=deadline-java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        var rng=new Random(settings.seed);var evaluator=new Evaluator(input);var population=new ArrayList<Score>();
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
