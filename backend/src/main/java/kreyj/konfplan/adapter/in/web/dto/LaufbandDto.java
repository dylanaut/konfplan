package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public class LaufbandDto {
    public List<String> news;

    public LaufbandDto(List<String> news) {
        this.news = news;
    }
}
