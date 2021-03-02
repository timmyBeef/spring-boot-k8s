# Spring Boot on K8S



## 產生一個這次要用來測試的 spring boot 
Spring Web, Spring Boot Actuator 一定要加, 其他只是之後還要測試用的...可以不加
![](https://i.imgur.com/4QIlodZ.png)


在啟動入口 SpringBootK8sApplication.java 這我有多寫一個 rest api 來做測試, 主要就是從設定檔讀取 hello.color 這個值
```java=
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@RestController
public class SpringBootK8sApplication {

    @Value("${hello.color}")
    String color;

    @GetMapping("/color")
    public String helloColor() {
        return color;
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringBootK8sApplication.class, args);
    }

}
```


透過 spring boot 2.3 後內建提供的打包 docker 的方式來打包
```bash=
./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=spring-boot-k8s
```

測試是否能正常運作
```bash=
$ docker run -p 8080:8080 --name spring-boot-k8s -t spring-boot-k8s
```

actuator health 和 自己建立的 api 都正常執行
![](https://i.imgur.com/YSbggwa.png)



## Tag docker image
因為在這測試的方式是, 不想把 docker image push 到 dockerhub(其實我想推...但這個都推不上去＠＠“)

所以如果想要讀取 local 端的 image 就好的話, image tag 要改一下

## K8S 預設的 pull policy
如果 docker image tag 是 latest, k8s 預設的 pull policy 就會是 Always
其他 tag： 預設 pull policy: IfNotPresent

想要讀取 local 端的 image 的 pull policy： 直接在設定成 Never 或 IfNotPresent

也可以直接把 image tag 設成像這樣 => pull policy 就是 IfNotPresent
```
$ docker tag spring-k8s/spring-boot-k8s spring-k8s/gs-spring-boot-k8s:snapshot

```

# local K8S - kind

在本機玩 K8S, 除了 minikube 還可以用另一套 kind 來玩（這裡主要是為了使用 kind load 把 image 直接讀進本地使用）

https://kind.sigs.k8s.io/docs/user/quick-start#installation

以下是 mac 下的安裝例子, 其他作業系統可以參考上面連結
```bash=
brew install kind

kind create cluster
```



![](https://i.imgur.com/xtNvg79.png)

把打包好的 image 讀取到 k8s 本地端
```bash=
$ kind load docker-image spring-k8s/gs-spring-boot-k8s:snapshot
```

## in kind cluster

偷懶用, 這樣之後都打 k 就好
```
alias k=kubectl
```

![](https://i.imgur.com/IfbkMjj.png)

## 建立 deployment & service

### 建立 deployment yaml
無論是建立 pod, deployment service, 千萬不要自己傻傻的打 yaml, k8s 都可以幫你產生各種的 yaml 檔案, 產生後再去調整就好 
* `k create deployment` 可以幫你建立 deployment
* `-o yaml` 輸出檔是 yaml
* `--dry-run=client` 只要輸出必要的設定就好

最後再利用 > 輸出到 deployment.yaml 這個檔案
```bash=
k create deployment spring-boot-k8s --image spring-k8s/spring-boot-k8s:snapshot -o yaml --dry-run=client > deployment.yaml
```

### 建立 service yaml
service 也是類似做法, 這裏用的是 clusterip 開放的 port 是 80
```bash=
k create service clusterip spring-boot-k8s --tcp 80:8080 -o yaml --dry-run=client > service.yaml
```

### 建立
```bash=
k apply -f .
```

![](https://i.imgur.com/3ssd8Qi.png)
從這可以看出都建立好了
![](https://i.imgur.com/esc2dov.png)

<br>

## 如果想要連到服務的話
目前無法直接連到這個服務, 除非知道目前 cluserIP 並且剛剛的 service 改成 NodePort 對外開放

所以先使用 port-forward 的功能, 這樣就可以從電腦本機打到, 這裏開的 port 是 9090
```
k port-forward svc/spring-boot-k8s 9090:80
```

測試成功
![](https://i.imgur.com/0xfYn1u.png)

![](https://i.imgur.com/dU8gd9A.png)

![](https://i.imgur.com/I1PEp51.png)

![](https://i.imgur.com/ms6efF3.png)



# livenessProbe, readinessProbe, lifecycle

在 pod 內 containers 第一個內加上
```
  livenessProbe:
	httpGet:
	  path: /actuator/health/liveness
	  port: 8080
	initialDelaySeconds: 30
	periodSeconds: 30
	failureThreshold: 5
  readinessProbe:
	httpGet:
	  path: /actuator/health/readiness
	  port: 8080
	initialDelaySeconds: 15
	periodSeconds: 30
	failureThreshold: 5
  lifecycle:
	preStop:
	  exec:
		command: [ "sh", "-c", "sleep 10" ]
```

![](https://i.imgur.com/jl28WXb.png)

* initialDelaySeconds
* periodSeconds
* failureThreshold

這三個後來設的長一點, 不然會發現一開始就做太多次的 retry 了
![](https://i.imgur.com/YEs0FZE.png)

# ConfigMaps

這裡使用 volume 的方式掛載 configmap

先把原本 spring boot 的設定檔 application.yaml 複製出來
![](https://i.imgur.com/ZPLgGzh.png)
<br>
這裏有多加上 spring boot 2.3 後提供的 graceful shutdown
```yaml=
server:
  shutdown: graceful
management:
  endpoints:
    enabled-by-default: true
    web:
      exposure:
        include: "*"
hello:
  color: green


```

#### 把 application.yaml 讀入 configmap
![](https://i.imgur.com/igi4Bx2.png)
<br>
#### 調整 deployment.yaml
```yaml=
apiVersion: apps/v1
kind: Deployment
metadata:
  creationTimestamp: null
  labels:
    app: spring-boot-k8s
  name: spring-boot-k8s
spec:
  replicas: 1
  selector:
    matchLabels:
      app: spring-boot-k8s
  strategy: { }
  template:
    metadata:
      creationTimestamp: null
      labels:
        app: spring-boot-k8s
    spec:
      containers:
        - image: spring-k8s/spring-boot-k8s:snapshot
          name: spring-boot-k8s
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 30
            failureThreshold: 5
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 30
            failureThreshold: 5
          lifecycle:
            preStop:
              exec:
                command: [ "sh", "-c", "sleep 10" ]
          volumeMounts:
          - name: config-volume
            mountPath: /workspace/config
          resources: { }
      volumes:
      - name: config-volume
        configMap:
          name: spring-boot-k8s
status: { }

```
<br>

仔細來看一下加了什麼
![](https://i.imgur.com/O7BVUGq.png)

### 這裡為什麼要設 /workspace/config 呢?
根據 spring 讀取設定檔的特性
https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-files

這裏第 4, 5 點描述了, 在當前目錄下定義的 /config 目錄裡的設定檔案, 將會覆蓋之前有的設定檔
![](https://i.imgur.com/M9cB0fU.png)

而依照 spring-boot 內建的打包 docker 的方式, 會將程式打包到 /workspace 資料夾下

<br>

可以透過指令進入 pod 一探究竟
```bash=
k exec -it pod-name -- bin/bash
```
![](https://i.imgur.com/JmIfF3i.png)

可以看到的確目錄下有個 BOOT-INF, 裡面有 classes, lib 等等, classes 下也有之前打包進去的 application.yaml
![](https://i.imgur.com/bqdXzve.png)

但因為在外層的 /config 這個目錄下, 我們已經透過 volumn 的方式把設定檔放入了, 所以之後 configmap 更新後, 就會自動更新到 /config 下的設定檔, 接著在對 deployment 做 rolling update, 自然就會吃到新的設定檔

所以進入 container 內可以看到目前 /config 下的設定檔正確的吃入了最新設定的 configmap
![](https://i.imgur.com/zpbWPzP.png)





正確讀到設定檔, 仔細一看, 其實這裡顯示了 config/application.yaml
![](https://i.imgur.com/ZhlnKGh.png)

也有顯示原先的設定檔, 所以來改一下 configmap 內的 color green 改成 red 來驗證看看, 是否會吃 configmap 的設定
![](https://i.imgur.com/u8NLls4.png)


## 測試修改 configmap

這裏我直接對 configmap 做 edit
![](https://i.imgur.com/Oor05Xc.png)

改成 red (原本是 green)
![](https://i.imgur.com/0ADR86C.png)

改完就生效了（不會像 pod 或 deployment 還要 apply tmp yaml)
![](https://i.imgur.com/4dqM30j.png)


### 做 rolling update
![](https://i.imgur.com/jgAFVRO.png)

接著觀察 pod 可以觀察到最後, 一旦新的 container 啟動了, 舊的就會進入 terminaing 的狀態
![](https://i.imgur.com/EUCL3zu.png)

再次把 port forward 出去
![](https://i.imgur.com/TF3as9t.png)

觀察結果, 成功更新！
![](https://i.imgur.com/OplYloG.png)


到這裡就大致完成 configmap + ap 啟動的一些小設定


# Reference
https://spring.io/guides/topicals/spring-on-kubernetes/

https://kind.sigs.k8s.io/docs/user/quick-start/#interacting-with-your-cluster


# K8S 的一些基本指令

## kubectl explain

explain 這指令非常實用

這樣可以列出所有 pod, deployemt, service 等等的結構內容
```bash=
kubectl explain pods --recursive | less
```
![](https://i.imgur.com/Ix469HO.png)

接著可以透過 /關鍵字 來搜尋你要的欄位
![](https://i.imgur.com/6RgLxN9.png)

或是透過 grep 來過濾, 這裏 -A8 代表下面八行, 也就是我要看 envFrom 下面八行的資料
```bash=
kubectl explain pods --recursive | grep -A8 envFrom
```
![](https://i.imgur.com/PMuD6o4.png)

## kubectl -h

就是指令 help
![](https://i.imgur.com/Li2am6r.png)


## Update Pod

如果沒有 pod 的 yaml 檔案時, 針對已經存在的 pod 可以指定 output to yaml

```bash=
kubectl get pod <pod-name> -o yaml > pod-definition.yaml
```

得到 yaml 後就可以修改 yaml 

修改後就可以用 `kubectl apply` 來更新 pod

```bash=
kubectl apply -f pod-definition.yml
```

也可以建立 pod 時也一起輸出 yaml

```bash=
kubectl run redis --image=redis --dry-run=client -o yaml > pod-definition.yaml
```

—dry-run=client表示一些default的參數先拿掉，暫時不需要submit出去 (通常在輸出yaml時使用)，若要直接創建物件不輸出yaml就無需加此參數

## Edit Pod

這樣可以直接進 pod 修改資訊

```bash=
kubectl edit pod pod名稱
```

## Delete Pod

```bash=
kubectl delete pod webapp
```

## Describe

```bash=
kubectl describe pod newpod-
kubectl get pods -o wide
```

## service

### cluserIP （預設
```bash=
kubectl expose pod redis --port=6379 --name redis-service --dry-run=client -o yaml
```


```bash=
kubectl create service clusterip redis --tcp=6379:6379 --dry-run=client -o yaml
```

### NodePort
```bash=
kubectl expose pod nginx --port=80 --name nginx-service --type=NodePort --dry-run=client -o yaml`
```


```bash=
kubectl create service nodeport nginx --tcp=80:80 --node-port=30080 --dry-run=client -o yaml
```