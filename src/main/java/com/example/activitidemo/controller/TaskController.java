package com.example.activitidemo.controller;

import com.example.activitidemo.model.AskLeave;
import com.example.activitidemo.service.AskLeaveService;
import org.activiti.engine.*;
import org.activiti.engine.impl.identity.Authentication;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Comment;
import org.activiti.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by tomoya at 2019/4/22
 */
@Controller
@RequestMapping("/task")
public class TaskController extends BaseController {

  @Autowired
  private RuntimeService runtimeService;
  @Autowired
  private TaskService taskService;
  @Autowired
  private IdentityService identityService;
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private ProcessEngine processEngine;
  @Autowired
  private HistoryService historyService;
  @Autowired
  private AskLeaveService askLeaveService;

  @GetMapping("/list")
  public String list(Model model) {
    List<Map> list = new ArrayList<>();
    List<Task> tasks = taskService.createTaskQuery().taskAssignee(getUser().getUsername()).list();
    for (Task task : tasks) {
      Map map = new HashMap();
      map.put("task", task);
      ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();
      AskLeave askLeave = askLeaveService.findById(Integer.parseInt(processInstance.getBusinessKey()));
      if (getUser().getId().equals(askLeave.getUser().getId())) {
        map.put("myTask", true);
      } else {
        map.put("myTask", false);
      }
      list.add(map);
    }
    model.addAttribute("tasks", list);
    return "myTasks";
  }

  @PostMapping("/queryTaskComments")
  @ResponseBody
  public Object queryTaskComments(String taskId) {
    Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
    List<Comment> comments = taskService.getProcessInstanceComments(task.getProcessInstanceId());
    return comments;
  }

  @PostMapping("/completeTask")
  @ResponseBody
  public Object completeTask(String taskId, String content, String pass) {
    System.out.println(pass);
    Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
    String processInstanceId = task.getProcessInstanceId();
    // 拿到请假记录的id
    ProcessInstance instance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
    Integer askLeaveId = Integer.parseInt(instance.getBusinessKey());

    Authentication.setAuthenticatedUserId(getUser().getUsername());
    taskService.addComment(taskId, processInstanceId, content);
    Map<String, Object> variables = new HashMap<>();
    variables.put("pass", pass);
    if (getUser().getLeader() != null) {
      variables.put("username", getUser().getLeader().getUsername());
    }
    taskService.complete(taskId, variables);
    // 判断流程是否走完 重新获取一次流程实例，如果为空 则表示流程结束了
    instance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
    if (instance == null) {
      // 更新请假状态
      AskLeave askLeave = askLeaveService.findById(askLeaveId);
      askLeave.setStatus("通过");
      askLeaveService.save(askLeave);
    }
    return true;
  }

}
